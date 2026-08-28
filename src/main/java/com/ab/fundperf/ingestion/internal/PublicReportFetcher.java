package com.ab.fundperf.ingestion.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.NoSuchElementException;

@Component
public class PublicReportFetcher {

    private final WebClient edgarClient = WebClient.builder()
            .baseUrl("https://efts.sec.gov")
            .defaultHeader(HttpHeaders.USER_AGENT, "fund-app-prototype you@example.com")
            .build();

    public Mono<String> findFilingDocumentUrl(String fundName) {
        return edgarClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/LATEST/search-index")
                        .queryParam("q", fundName + " quarterly report")
                        .queryParam("forms", "10-Q,N-CSR")
                        .build())
                .retrieve()
                .bodyToMono(EdgarSearchResponse.class)
                .handle((response, sink) -> {
                    try {
                        sink.next(firstResultDocumentUrl(response));
                    } catch (NoSuchElementException e) {
                        sink.error(new FundReportNotFoundException(fundName));
                    }
                });
    }

    private String firstResultDocumentUrl(EdgarSearchResponse response) {
        Hit hit = response.hits().hits().stream().findFirst()
                .orElseThrow(NoSuchElementException::new);

        String[] idParts = hit.id().split(":", 2);
        String accession = idParts[0];
        String filename = idParts.length > 1 ? idParts[1] : "";
        String accessionNoDashes = accession.replace("-", "");
        int cik = Integer.parseInt(hit.source().ciks().getFirst());

        return "https://www.sec.gov/Archives/edgar/data/%d/%s/%s"
                .formatted(cik, accessionNoDashes, filename);
    }
}

record EdgarSearchResponse(Hits hits) {}

record Hits(List<Hit> hits) {}

record Hit(
        @JsonProperty("_id") String id,
        @JsonProperty("_source") Source source
) {}

record Source(
        @JsonProperty("display_names") List<String> displayNames,
        List<String> ciks,
        String form,
        @JsonProperty("file_date") String fileDate,
        String adsh
) {}

class FundReportNotFoundException extends RuntimeException {
    FundReportNotFoundException(String fundName) {
        super("No EDGAR filing found for fund: " + fundName);
    }
}