# 01 — Prerequisites & Installation

Target environment: Windows + WSL2, Docker running inside WSL, 16GB RAM, 256GB SSD.

## 1.1 Java 25

Check what you have first:
```bash
java -version
```

If it's not 25, install via [SDKMAN](https://sdkman.io/) (works inside WSL, easiest way to manage JDKs):
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem     # Temurin build of Java 25
sdk use java 25-tem
java -version               # confirm: openjdk version "25"
```

**Why this matters:** Spring Boot 3.5.x officially supports Java 25, but your IDE/Maven toolchain may still default to an older JDK it finds first on PATH. Set it explicitly (see 02).

## 1.2 Maven

```bash
sdk install maven
mvn -version    # confirm it's picking up Java 25
```

## 1.3 Docker in WSL — confirm it's set up correctly

You said Docker is already running in WSL. Two valid setups:
- **Docker Desktop with WSL2 backend** (Windows-side app, WSL integration enabled) — most common
- **Docker Engine installed natively inside the WSL distro** (no Docker Desktop)

Either way, confirm:
```bash
docker version
docker compose version
```

**Networking note (important for later, esp. Claude Desktop):** with Docker Desktop's WSL2 backend, `localhost` from Windows reaches containers/services running in WSL automatically — WSL2's default networking mode forwards `localhost` both directions. If you installed Docker natively inside WSL without Docker Desktop, confirm the same by running something on a port in WSL and hitting `http://localhost:<port>` from a Windows browser. If that doesn't work, you're on an older WSL networking mode — this is a real environment-specific gotcha, worth confirming *now* rather than debugging later when Claude Desktop can't connect.

## 1.4 Ollama

Install (inside WSL, so it shares the filesystem/network namespace cleanly with your Docker containers):
```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &          # if not already running as a service
curl http://localhost:11434/api/tags   # confirm it responds
```

Pull the two models you need:
```bash
ollama pull nomic-embed-text              # ~274MB — embeddings
ollama pull qwen2.5:7b-instruct-q4_K_M    # ~4.7GB — chat + tool-calling
```

Confirm tool-calling support on the chat model (needed for MCP tool invocation to work):
```bash
ollama show qwen2.5:7b-instruct-q4_K_M
# check the output lists "tools" under capabilities
```

If it doesn't, `mistral:7b-instruct` is a solid known-good fallback for tool-calling with Spring AI's Ollama integration.

## 1.5 ChromaDB

Run it as a container — this is the only piece we containerize at this stage (the app and Ollama stay as local processes until page 07):

```bash
docker run -d --name chromadb \
  -p 8000:8000 \
  -v chroma-data:/chroma/chroma \
  chromadb/chroma:latest

curl http://localhost:8000/api/v2/heartbeat   # confirm it's up
```

## 1.6 Sanity checklist before moving on

```bash
java -version                          # 25
mvn -version                           # picks up Java 25
docker ps                              # chromadb container running
curl http://localhost:11434/api/tags   # ollama models listed
curl http://localhost:8000/api/v2/heartbeat   # chroma healthy
```

All five green → move to [02 — Project Structure](02-project-structure.md).
