# 07 — Docker Deployment (WSL)

So far the app has run as a local process (`mvn spring-boot:run`) against a containerized ChromaDB and a host-installed Ollama. This page containerizes the app itself too, so the full stack (app + Chroma + Ollama) runs as one `docker compose up` inside WSL.

## 7.1 Dockerfile (multi-stage build)

`Dockerfile` at project root:
```dockerfile
# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Using a JRE (not JDK) for the runtime stage keeps the image smaller — matters on a 256GB SSD once you're iterating on rebuilds.

## 7.2 `docker-compose.yml` — full stack

```yaml
services:
  chromadb:
    image: chromadb/chroma:latest
    ports: ["8000:8000"]
    volumes: ["chroma-data:/chroma/chroma"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v2/heartbeat"]
      interval: 10s
      timeout: 5s
      retries: 5

  ollama:
    image: ollama/ollama:latest
    ports: ["11434:11434"]
    volumes: ["ollama-data:/root/.ollama"]
    deploy:
      resources:
        limits:
          memory: 8g       # cap it — you have 16GB total and the JVM + Chroma need headroom too

  fund-app:
    build: .
    ports: ["8080:8080"]
    depends_on:
      chromadb:
        condition: service_healthy
      ollama:
        condition: service_started
    environment:
      - SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434
      - SPRING_AI_VECTORSTORE_CHROMA_CLIENT_HOST=http://chromadb
      - SPRING_AI_VECTORSTORE_CHROMA_CLIENT_PORT=8000
    volumes:
      - app-data:/app/data   # H2 event registry file, persisted across restarts

volumes:
  chroma-data:
  ollama-data:
  app-data:
```

**Important:** inside Docker Compose's network, containers reach each other by **service name**, not `localhost` — that's why `SPRING_AI_OLLAMA_BASE_URL` becomes `http://ollama:11434` and Chroma's host becomes `http://chromadb` here, overriding the `localhost`-based values in `application.yml` that were correct for the page 01–06 local-process setup. Environment variables override `application.yml` properties automatically in Spring Boot (relaxed binding: `SPRING_AI_OLLAMA_BASE_URL` → `spring.ai.ollama.base-url`).

## 7.3 If you already had Ollama running as a host process (page 01)

You don't have to containerize Ollama — it's a reasonable choice to keep it on the WSL host and only containerize the app + Chroma, since Ollama benefits from direct access to WSL's GPU passthrough (if you have one configured) without a container layer in between. In that case, drop the `ollama` service from the compose file and instead point `fund-app` at the host:

```yaml
    environment:
      - SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

`host.docker.internal` resolves to the WSL host from inside a container on Docker Desktop's WSL2 backend. If you're on Docker Engine installed natively in WSL (no Docker Desktop), this DNS name may not resolve by default — add it explicitly:
```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

## 7.4 Bring it up

```bash
docker compose build
docker compose up -d
docker compose logs -f fund-app     # watch startup
```

Pull models into the Ollama container if you containerized it (models aren't baked into the image):
```bash
docker compose exec ollama ollama pull nomic-embed-text
docker compose exec ollama ollama pull qwen2.5:7b-instruct-q4_K_M
```

Confirm the same health checks from page 01, now against the compose stack:
```bash
curl http://localhost:8080/actuator/health   # add spring-boot-starter-actuator if you want this endpoint
curl http://localhost:8000/api/v2/heartbeat
curl http://localhost:11434/api/tags
```

## 7.5 WSL ↔ Windows networking — why this matters for page 09

Everything above binds to `localhost` on ports 8000/8080/11434 **from inside WSL**. With Docker Desktop's WSL2 integration (the default, and what you're using), WSL2's networking mode forwards these ports so `http://localhost:8080` also works **from Windows** — this is what lets Claude Desktop (a Windows app) reach your MCP server on page 09 without extra tunneling.

Confirm this explicitly now, don't assume it:
```powershell
# from a Windows PowerShell prompt, not inside WSL
curl http://localhost:8080/mcp
```
If this fails from Windows but works from inside WSL, you're likely on an older WSL "NAT" networking mode rather than the newer "mirrored" mode — check `wsl --status` and consider enabling mirrored networking in `.wslconfig` (`networkingMode=mirrored`), or fall back to the `mcp-remote` bridge approach in page 09, which works regardless of networking mode since it runs the bridge as a Windows-side Node process.

Next: [08 — Using the Chat Window](08-connect-chat-window.md).
