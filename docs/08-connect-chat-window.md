# 08 — Using the Chat Window

## 8.1 Open it

```
http://localhost:8080/index.html
```

If you deployed via Docker (page 07), this is the same URL — port 8080 is published to the host either way.

## 8.2 Ingest something first

The chat window is only as good as what's been ingested. Before asking questions, run at least one ingest (from page 03):

```bash
curl -X POST http://localhost:8080/api/ingest/by-fund-name \
     -H "Content-Type: application/json" \
     -d '{"fundName":"Vanguard 500 Index Fund"}'
```

Or upload a PDF you already have:
```bash
curl -F "file=@quarterly-report.pdf" -F "fundName=My Test Fund" \
     http://localhost:8080/api/ingest/upload
```

## 8.3 Ask questions

Type into the chat window, e.g.:
- *"What funds do you have data on?"* → should trigger `listIngestedFunds`
- *"What was the Vanguard 500 Index Fund's performance last quarter?"* → should trigger `searchFundDocuments`, retrieve chunks, and answer grounded in them

## 8.4 What "working correctly" looks like

Watch the app logs (`docker compose logs -f fund-app` or your terminal if running locally) while you ask a question. You should see, in order:
1. The `ApplicationRunner` startup log from page 05 confirming tools were discovered
2. A tool-call decision from Ollama (visible in Spring AI's debug logs if you enable `logging.level.org.springframework.ai=DEBUG`)
3. The MCP round-trip to `/mcp` on the same app
4. `searchFundDocuments` executing a Chroma similarity search
5. The final streamed answer

If the answer comes back generic/ungrounded (no fund-specific figures), the model likely isn't calling the tool at all — see page 10 for the specific fix.

## 8.5 A second useful check: query the vector store directly

To separate "retrieval is broken" from "the model isn't calling the tool," bypass the chat entirely and hit the MCP tool via the Inspector (page 04) or a temporary debug REST endpoint that calls `vectorStore.similaritySearch(...)` directly. If that returns good results but the chat answer doesn't reflect them, the problem is in tool-calling/prompting, not retrieval — a much narrower thing to debug.

Next: [09 — Connecting Claude Desktop](09-connect-claude-desktop.md).
