# Fund Performance Report RAG + MCP Prototype

A local, single-JVM (Spring Modulith) application that:
1. Ingests fund quarterly/performance reports — either an uploaded PDF or auto-fetched by fund name
2. Vectorizes them (Ollama embeddings → ChromaDB)
3. Exposes the data as MCP tools over a real MCP server
4. Serves a chat window that uses those tools (and can be reused by Claude Desktop)

**Stack:** Java 25 · Spring Boot 3.5.x · Spring Modulith 1.4.11 · Spring AI 1.1.x (WebFlux/reactive) · Ollama · ChromaDB · Docker (WSL)

## How to use this documentation

Read the pages in order — each one assumes the previous steps are done. Pages 01–06 build the app, 07 deploys it in Docker under WSL, 08–09 connect the two chat clients (your own UI, and Claude Desktop).

| # | Page | What you'll do |
|---|------|-----------------|
| 01 | [Prerequisites & Installation](01-prerequisites-installation.md) | Install Java 25, Maven, Docker in WSL, Ollama, pull models, run ChromaDB |
| 02 | [Project Structure (Spring Modulith)](02-project-structure.md) | Create the Maven project, module layout, dependencies, config |
| 03 | [Ingestion Module](03-ingestion-module.md) | Build the 2 input APIs, PDF parsing, chunking, embedding |
| 04 | [MCP Server Module](04-mcp-server-module.md) | Expose `@Tool` methods over MCP |
| 05 | [Chat Module](05-chat-module.md) | ChatClient + MCP client wiring + chat UI |
| 06 | [Events & Module Boundary Tests](06-events-and-testing.md) | Cross-module events, `verify()` test |
| 07 | [Docker Deployment (WSL)](07-docker-deployment.md) | Dockerfile, docker-compose, running the full stack in WSL |
| 08 | [Using the Chat Window](08-connect-chat-window.md) | End-to-end test of your own UI |
| 09 | [Connecting Claude Desktop](09-connect-claude-desktop.md) | Point Claude Desktop at your MCP server |
| 10 | [Troubleshooting](10-troubleshooting.md) | Common failure modes and fixes |

## Architecture

```
                 ┌───────────────────────────────────────────────┐
                 │            fund-app (single JVM)               │
                 │                                                 │
  PDF upload --->│  [ingestion module]                            │
  Fund name  --->│    - PDF/EDGAR intake                          │
                 │    - chunk + embed  ───────► ChromaDB (Docker)  │
                 │           │ publishes FundIngestedEvent          │
                 │           ▼                                     │
                 │  [mcpserver module]                            │
                 │    - @Tool searchFundDocuments()               │
                 │    - @Tool listIngestedFunds()                 │
                 │    - exposes /mcp (streamable HTTP)             │
                 │           ▲                    ▲                │
                 │           │ MCP                │ MCP            │
                 │  [chat module]          Claude Desktop          │
                 │    - ChatClient + Ollama                        │
                 │    - HTML chat window                           │
                 └───────────────────────────────────────────────┘
                              │
                              ▼
                      Ollama (Docker/WSL host)
```

One deployable JVM, three Spring Modulith modules with enforced boundaries, one real MCP endpoint that both your own chat UI and Claude Desktop can connect to.
