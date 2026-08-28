# 10 — Troubleshooting

## App hangs or times out under any concurrent request

**Cause:** a blocking call (PDFBox, Tika, the Chroma HTTP client) running directly on a WebFlux event-loop thread.
**Fix:** confirm every ingestion path is wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` as shown in page 03. This is the single most common WebFlux+RAG bug.

## Chat answers are generic / not grounded in ingested data

**Likely cause:** the model isn't calling `searchFundDocuments` at all.
**Diagnose:**
1. Enable `logging.level.org.springframework.ai=DEBUG` and watch for a tool-call decision in the logs.
2. Test the tool directly via MCP Inspector (page 04) to rule out a retrieval problem.
3. Test via Claude Desktop (page 09) — if Claude Desktop grounds its answer correctly and your local model doesn't, the issue is Qwen2.5's tool-calling reliability at this quantization, not your pipeline. Try `mistral:7b-instruct` or a less-quantized Qwen build.
4. Check the system prompt in `ChatClientConfig` is actually being applied — a missing `.defaultSystem(...)` means the model has no instruction to prefer tool use over guessing.

## `ollama show <model>` doesn't list tool-calling support

Some quantizations strip or degrade function-calling reliability. Prefer `qwen2.5:7b-instruct-q4_K_M` or `mistral:7b-instruct` — both have been reliable with Spring AI's `OllamaChatModel` tool integration.

## `PagePdfDocumentReader` throws on a specific PDF

Common with scanned reports or PDFs using unusual embedded fonts. The try/catch fallback to `TikaDocumentReader` in page 03 handles most of these automatically. If a PDF fails both readers, it's likely a pure-image scan with no text layer — that needs OCR (out of scope for this prototype; Apache Tika with Tesseract configured is the usual next step).

## `ApplicationModules.verify()` fails

Read the failure message — it names the exact package boundary crossed. Common cause: importing something from `<module>/internal/**` in another module's code, or a circular dependency between two modules (e.g. `ingestion` depending on something in `mcpserver` while `mcpserver` also depends on `ingestion`). Fix by routing the interaction through an event instead (page 06) or by extracting the shared type into whichever module should own it.

## Docker Compose: `fund-app` can't reach Chroma/Ollama

**Cause:** using `localhost` instead of the Compose service name inside container-to-container config.
**Fix:** confirm the environment overrides in page 07.2 (`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`, etc.) are actually applied — `docker compose exec fund-app env | grep SPRING_AI` to check.

## Claude Desktop shows the connector but 0 tools, or fails to connect

1. Confirm `curl http://localhost:8080/mcp` works **from Windows PowerShell**, not just WSL (page 07.5 / 09.3). This is the most common root cause — WSL2 NAT-mode networking not forwarding the port to Windows.
2. Check `%APPDATA%\Claude\logs\mcp-server-fund-reports.log` for the actual `mcp-remote` connection error.
3. Confirm `--transport http-only` matches your server — if you changed `spring.ai.mcp.server.type` away from the streamable-HTTP default, the transport flag needs to match.
4. Fully quit and restart Claude Desktop after any config change — it only reads `claude_desktop_config.json` at startup.

## Chroma collection is empty after ingestion "succeeds"

Check `initialize-schema: true` is set (page 02) — without it, Chroma may reject writes to a collection that doesn't exist yet. Also confirm `vectorStore.add(chunks)` in `DocumentIngestionService` isn't silently swallowing an exception — wrap it in a try/catch that logs, at least during development, since `Mono.fromCallable` will otherwise surface the error only as a generic 500.

## RAM pressure (swapping, sluggish responses)

On 16GB: a 7B q4 chat model (~5GB resident) + Chroma + one JVM running all three Modulith modules is close to the edge if you also have Docker Desktop, an IDE, and a browser with many tabs open. Concretely:
- Drop to `llama3.2:3b` (~2GB) if things feel sluggish, accepting weaker tool-calling reliability
- Set the `ollama` container's memory limit explicitly (page 07.2) so it can't balloon
- Keep `topK` in `searchFundDocuments` small (5, not 20+) — this bounds how much context gets passed to the chat model per turn
