package com.ab.fundperf.ingestion;

import com.ab.fundperf.ingestion.internal.PublicReportFetcher;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final ApplicationEventPublisher events;
    private final PublicReportFetcher reportFetcher;
    private final WebClient documentDownloadClient;

    public DocumentIngestionService(VectorStore vectorStore,
                                    ApplicationEventPublisher events,
                                    PublicReportFetcher reportFetcher,
                                    WebClient.Builder webClientBuilder) {
        this.vectorStore = vectorStore;
        this.events = events;
        this.reportFetcher = reportFetcher;
        // separate client from EDGAR's — this one fetches the actual PDF bytes,
        // which live on www.sec.gov (a different host than efts.sec.gov)
        this.documentDownloadClient = webClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, "fund-app-prototype you@example.com")
                .build();
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

    public Mono<IngestResponse> fetchAndIngestPublicReport(String fundName) {
        return reportFetcher.findFilingDocumentUrl(fundName)
                .flatMap(documentUrl -> documentDownloadClient.get()
                        .uri(documentUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .flatMap(pdfBytes -> ingestPdfBytes(pdfBytes, fundName, documentUrl)));
    }
}