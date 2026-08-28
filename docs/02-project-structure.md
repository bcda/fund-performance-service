# 02 — Project Structure (Spring Modulith)

## 2.1 Generate the project

Go to [start.spring.io](https://start.spring.io) (or use the CLI equivalent):
- **Project:** Maven
- **Language:** Java
- **Spring Boot:** 3.5.x (latest GA)
- **Java:** 25
- **Group/Artifact:** `com.you:fund-app`
- **Dependencies to add:** Spring Reactive Web, Spring Modulith

Download, unzip, `cd fund-app`.

## 2.2 `pom.xml` — full dependency set

```xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.release>25</maven.compiler.release>
    <spring-ai.version>1.1.0</spring-ai.version>
    <spring-modulith.version>1.4.11</spring-modulith.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Modulith core + event publication registry (JDBC-backed) -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Ollama chat + embeddings -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>

    <!-- Chroma vector store -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
    </dependency>

    <!-- MCP server (reactive) + MCP client -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
    </dependency>

    <!-- PDF parsing (+ Tika fallback for awkward PDFs) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pdf-document-reader</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-tika-document-reader</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> `spring-modulith-starter-jdbc` + H2 gives you the event publication registry (durable delivery for `@ApplicationModuleListener`) with zero extra setup — H2 file-mode is fine for a prototype. Swap to Postgres later if you containerize state.

## 2.3 Package layout

```
src/main/java/com/you/fundapp/
├── FundAppApplication.java
├── ingestion/
│   ├── IngestionController.java          <- module's public API surface
│   ├── DocumentIngestionService.java
│   ├── FundIngestedEvent.java            <- published event (public, other modules react to it)
│   └── internal/
│       ├── PublicReportFetcher.java      <- EDGAR client, not visible outside this module
│       └── PdfParsingSupport.java
├── mcpserver/
│   ├── FundReportTools.java              <- @Tool methods
│   ├── McpToolConfig.java
│   └── internal/
│       └── IngestedFundsRegistry.java
└── chat/
    ├── ChatController.java
    └── ChatClientConfig.java

src/main/resources/
├── application.yml
└── static/
    └── index.html                        <- chat window
```

Each top-level package (`ingestion`, `mcpserver`, `chat`) is an **application module** — Spring Modulith infers this from package structure alone, no annotation required. Anything under `<module>/internal/**` is invisible to other modules; the compiler won't stop you from importing it, but `ApplicationModules.verify()` (page 06) will fail the build if you do.

## 2.4 `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: fund-app

  datasource:
    url: jdbc:h2:file:./data/fund-app-events
    driver-class-name: org.h2.Driver

  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text
      chat:
        options:
          model: qwen2.5:7b-instruct-q4_K_M
          temperature: 0.2

    vectorstore:
      chroma:
        client:
          host: http://localhost
          port: 8000
        collection-name: fund_reports
        initialize-schema: true

    mcp:
      server:
        name: fund-reports-mcp-server
        version: 1.0.0
        type: ASYNC
        instructions: "Provides tools to search and summarize fund quarterly/performance reports that have been ingested."
        capabilities:
          tool: true
          resource: true
      client:
        sse:
          connections:
            fund-server:
              url: http://localhost:8080     # loopback — same app, same port
        toolcallback:
          enabled: true

  webflux:
    multipart:
      max-in-memory-size: 10MB
  codec:
    max-in-memory-size: 20MB

modulith:
  events:
    jdbc-schema-initialization:
      enabled: true
```

At this point `mvn spring-boot:run` should start cleanly (with no controllers yet) and connect to Ollama/Chroma without errors — worth confirming before writing any module code.

Next: [03 — Ingestion Module](03-ingestion-module.md).
