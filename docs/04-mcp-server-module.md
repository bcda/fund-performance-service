# 04 — MCP Server Module

Turns the ingested, vectorized data into tools any MCP client can discover and call — your own chat module (page 05) and, later, Claude Desktop (page 09).

## 4.1 `@Tool` methods

`mcpserver/FundReportTools.java`
```java
@Service
public class FundReportTools {

    private final VectorStore vectorStore;
    private final IngestedFundsRegistry registry;

    public FundReportTools(VectorStore vectorStore, IngestedFundsRegistry registry) {
        this.vectorStore = vectorStore;
        this.registry = registry;
    }

    @Tool(description = "Search ingested fund performance/quarterly reports for relevant passages. " +
                         "Use this to answer questions about fund performance, holdings, returns, or fees.")
    public String searchFundDocuments(
            @ToolParam(description = "The user's question or search phrase") String query,
            @ToolParam(description = "Optional exact fund name to restrict the search to", required = false) String fundName) {

        SearchRequest.Builder req = SearchRequest.builder().query(query).topK(5);
        if (fundName != null && !fundName.isBlank()) {
            req.filterExpression("fundName == '" + fundName + "'");
        }

        List<Document> results = vectorStore.similaritySearch(req.build());

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
```

`mcpserver/internal/IngestedFundsRegistry.java` — a simple in-memory registry kept current by listening to the ingestion module's event (wired in page 06):
```java
@Component
class IngestedFundsRegistry {
    private final Set<String> funds = ConcurrentHashMap.newKeySet();

    void add(String fundName) { funds.add(fundName); }
    List<String> getAll() { return List.copyOf(funds); }
}
```

## 4.2 Register the tools with the MCP server

`mcpserver/McpToolConfig.java`
```java
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider fundReportTools(FundReportTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
```

That's the whole wiring. With `spring-ai-starter-mcp-server-webflux` on the classpath (added in page 02) and this bean present, Spring Boot auto-configures a streamable-HTTP MCP endpoint at `/mcp` on port 8080 — the same port your app already serves on, per the `application.yml` from page 02.

## 4.3 Verify the MCP endpoint directly

```bash
curl -N http://localhost:8080/mcp
```
You should get an MCP protocol handshake response, not a 404. If you have `npx` available, the official [MCP Inspector](https://modelcontextprotocol.io) is worth running here — it gives you a UI to list tools and call them manually before any chat model is involved:
```bash
npx @modelcontextprotocol/inspector http://localhost:8080/mcp
```
Confirm `searchFundDocuments` and `listIngestedFunds` both show up and that calling `searchFundDocuments` with a query returns chunks from page 03's test ingestion.

**Do this check before page 05.** If tool discovery/calling is broken, debugging it through a chat model that decides *whether* to call the tool at all is much harder than debugging it directly.

Next: [05 — Chat Module](05-chat-module.md).
