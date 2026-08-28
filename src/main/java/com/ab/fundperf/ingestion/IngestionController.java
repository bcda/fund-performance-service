package com.ab.fundperf.ingestion;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.UUID;

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

    private Mono<Path> saveToTemp(FilePart filePart) {
        Path tempFile = Path.of(System.getProperty("java.io.tmpdir"),
                UUID.randomUUID() + "-" + filePart.filename());
        return filePart.transferTo(tempFile).thenReturn(tempFile);
    }
}

record FundIngestRequest(String fundName) {}
record IngestResponse(String fundName, int chunksIndexed, String sourceUrl) {}