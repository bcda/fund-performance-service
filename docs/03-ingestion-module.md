# 03 — Ingestion Module

Builds the two input APIs and the vectorization pipeline.

## 3.1 API 1 — upload a PDF directly

`ingestion/IngestionController.java`
```java
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final DocumentIngestionService ingestionService;

    public IngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<IngestResponse> uploadPdf(@RequestPart("file") FilePart file,
                                           @RequestPart("fundName") String fundName) {
        return ingestionService.ingestUploadedPdf(file, fundName);
    }

    @PostMapping("/by-fund-name")
    public Mono<IngestResponse> ingestByFundName(@RequestBody FundIngestRequest request) {
        return ingestionService.fetchAndIngestPublicReport(request.fundName());
    }
}

record FundIngestRequest(String fundName) {}
record IngestResponse(String fundName, int chunksIndexed, String sourceUrl) {}
```

Saving the reactive `FilePart` to disk before parsing (PDFBox needs a `File`/`InputStream`, not a reactive stream):
```java
private Mono<Path> saveToTemp(FilePart filePart) {
    Path tempFile = Path.of(System.getProperty("java.io.tmpdir"),
            UUID.randomUUID() + "-" + filePart.filename());
    return filePart.transferTo(tempFile).thenReturn(tempFile);
}
```

## 3.2 API 2 — fetch a public report by fund name

`ingestion/internal/PublicReportFetcher.java` — uses SEC EDGAR's full-text search API, which indexes real filed quarterly/annual reports (10-Q, N-CSR) for any US-listed fund, free and with no auth beyond a `User-Agent` header (SEC requires this to identify the caller):

```java
@Component
class PublicReportFetcher {

    private final WebClient edgarClient = WebClient.builder()
            .baseUrl("https://efts.sec.gov")
            .defaultHeader(HttpHeaders.USER_AGENT, "fund-app-prototype you@example.com")
            .build();

    Mono<String> findFilingDocumentUrl(String fundName) {
        return edgarClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/LATEST/search-index")
                        .queryParam("q", fundName + " quarterly report")
                        .queryParam("forms", "10-Q,N-CSR")
                        .build())
                .retrieve()
                .bodyToMono(EdgarSearchResponse.class)
                .map(this::firstResultDocumentUrl);
    }
}
```

**Prototype shortcut:** EDGAR's response shape and rate limits take iteration to get exactly right. To keep the ingestion pipeline demoable while you tune the EDGAR client, add a small static fallback map — Vanguard/iShares/SPDR all publish fact-sheet PDFs at stable public URLs with no auth:

```java
private static final Map<String, String> KNOWN_FUND_PDFS = Map.of(
    "vanguard 500 index fund", "https://institutional.vanguard.com/.../VFIAX_factsheet.pdf",
    "ishares core s&p 500 etf", "https://www.ishares.com/.../IVV_factsheet.pdf"
);
```
Fall back to this map when EDGAR returns nothing, or use it first and treat EDGAR as the "real" path once it's proven.

## 3.3 Parsing, chunking, embedding (the vectorization step)

`ingestion/DocumentIngestionService.java`
```java
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final ApplicationEventPublisher events;

    public DocumentIngestionService(VectorStore vectorStore, ApplicationEventPublisher events) {
        this.vectorStore = vectorStore;
        this.events = events;
    }

    public Mono<IngestResponse> ingestPdfBytes(byte[] pdfBytes, String fundName, String sourceUrl) {
        return Mono.fromCallable(() -> {
            Resource resource = new ByteArrayResource(pdfBytes);

            List<Document> pages;
            try {
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource,
                    PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(
                            ExtractedTextFormatter.builder().withNumberOfTopTextLinesToDelete(0).build())
                        .withPagesPerDocument(1)
                        .build());
                pages = reader.get();
            } catch (Exception pdfBoxFailure) {
                // fallback for scanned / odd-font PDFs PDFBox chokes on
                pages = new TikaDocumentReader(resource).get();
            }

            pages.forEach(doc -> {
                doc.getMetadata().put("fundName", fundName);
                doc.getMetadata().put("sourceUrl", sourceUrl);
                doc.getMetadata().put("ingestedAt", Instant.now().toString());
            });

            TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
            List<Document> chunks = splitter.apply(pages);

            vectorStore.add(chunks);   // embeds via nomic-embed-text, writes to Chroma

            events.publishEvent(new FundIngestedEvent(fundName, chunks.size(), Instant.now()));

            return new IngestResponse(fundName, chunks.size(), sourceUrl);
        }).subscribeOn(Schedulers.boundedElastic());
        // PDFBox/Tika and the Chroma HTTP client are blocking — never run them on the WebFlux event loop
    }

    public Mono<IngestResponse> ingestUploadedPdf(FilePart file, String fundName) {
        return DataBufferUtils.join(file.content())
            .map(buf -> {
                byte[] bytes = new byte[buf.readableByteCount()];
                buf.read(bytes);
                DataBufferUtils.release(buf);
                return bytes;
            })
            .flatMap(bytes -> ingestPdfBytes(bytes, fundName, "uploaded:" + file.filename()));
    }
}
```

`ingestion/FundIngestedEvent.java` (public — this is the module's event contract):
```java
public record FundIngestedEvent(String fundName, int chunksIndexed, Instant at) {}
```

### Why these choices
- **800-token chunks / 350 min:** quarterly reports have dense tables; too-small chunks split a table row across chunks and hurt retrieval. Tune after your first few ingests by inspecting what `searchFundDocuments` actually returns.
- **PDFBox → Tika fallback:** `PagePdfDocumentReader` is fast but throws on some scanned/odd-font PDFs; Tika is slower but more forgiving. Try/catch keeps ingestion resilient without you having to pre-classify PDFs.
- **`Schedulers.boundedElastic()`:** this is the single most common bug in a WebFlux app doing PDF/vector work — forgetting this stalls your whole reactive app under any concurrent load.
- **Metadata on every chunk before splitting:** lets `mcpserver` filter searches to one fund later (`fundName == 'X'`) instead of searching across everything you've ever ingested.

## 3.4 Test it directly (before wiring MCP)

```bash
curl -F "file=@vanguard-q2-report.pdf" -F "fundName=Vanguard 500 Index Fund" \
     http://localhost:8080/api/ingest/upload

curl -X POST http://localhost:8080/api/ingest/by-fund-name -H "Content-Type: application/json" -d '{"fundName":"Vanguard 500 Index Fund"}'
```

Then confirm the vectors actually landed:
```bash
curl http://localhost:8000/api/v2/collections
```

Next: [04 — MCP Server Module](04-mcp-server-module.md).
