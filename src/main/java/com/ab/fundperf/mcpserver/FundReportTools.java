package com.ab.fundperf.mcpserver;

import com.ab.fundperf.mcpserver.internal.IngestedFundsRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FundReportTools {

    private final VectorStore vectorStore;
    private final IngestedFundsRegistry registry;

    public FundReportTools(VectorStore vectorStore, IngestedFundsRegistry registry) {
        this.vectorStore = vectorStore;
        this.registry = registry;
    }

    @Tool(description = "Search ingested fund performance/quarterly reports for relevant passages...")
    public String searchFundDocuments(
            @ToolParam(description = "The user's question or search phrase") String query,
            @ToolParam(description = "Optional exact fund name to restrict the search to", required = false) String fundName) {

        System.out.println("=== searchFundDocuments called ===");
        System.out.println("query: " + query);
        System.out.println("fundName: " + fundName);

        SearchRequest.Builder req = SearchRequest.builder().query(query).topK(5);
/*        if (fundName != null && !fundName.isBlank()) {
            req.filterExpression("fundName == '" + fundName + "'");
        }*/

        List<Document> results = vectorStore.similaritySearch(req.build());

        System.out.println("Results found: " + results.size());

        if (results.isEmpty()) {
            return "No matching passages found. The fund may not be ingested yet — check listIngestedFunds.";
        }

        return results.stream()
                .map(d -> "[%s] %s".formatted(d.getMetadata().get("fundName"), d.getText()))
                .collect(Collectors.joining("\n---\n"));
    }

    @Tool(description = "List the distinct fund names that have been ingested and are available to query.")
    public List<String> listIngestedFunds() {
        return registry.getAll();
    }
}