# 05 — Chat Module

Your own chat UI, wired up as a real MCP client talking to the `mcpserver` module's `/mcp` endpoint — over loopback HTTP, since everything runs in one JVM/port here (see the architecture note in page 00 on why this is still "real" MCP, not a shortcut).

## 5.1 `ChatClient` wired with the MCP tools

`chat/ChatClientConfig.java`
```java
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbacks) {
        return builder
            .defaultSystem("""
                You are a fund performance assistant. Answer only using the
                searchFundDocuments tool for facts about specific funds — never
                guess figures. If the requested fund isn't found, call
                listIngestedFunds and tell the user what is available.
                Cite the fund name for every claim you make.
                """)
            .defaultToolCallbacks(mcpToolCallbacks)
            .build();
    }
}
```

`mcpToolCallbacks` here is auto-configured by `spring-ai-starter-mcp-client` from the `spring.ai.mcp.client.sse.connections.fund-server.url` entry in `application.yml` (page 02) — on startup it connects to your own app's `/mcp` endpoint, discovers `searchFundDocuments`/`listIngestedFunds`, and exposes them to `ChatClient` as callable tools. Log the discovered tool names at startup once to confirm the wiring:

```java
@Bean
ApplicationRunner logDiscoveredTools(ToolCallbackProvider mcpToolCallbacks) {
    return args -> Arrays.stream(mcpToolCallbacks.getToolCallbacks())
        .forEach(t -> System.out.println("Discovered MCP tool: " + t.getToolDefinition().name()));
}
```

## 5.2 Streaming chat endpoint

`chat/ChatController.java`
```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }
}
```

When the user asks *"how did the Vanguard 500 fund perform last quarter?"*, Qwen2.5's tool-calling decides to invoke `searchFundDocuments`, gets retrieved chunks back through the MCP round-trip, and grounds its answer in them. That's the full RAG loop, routed through MCP instead of a direct in-process vector-store call.

## 5.3 Minimal chat UI

`src/main/resources/static/index.html`
```html
<!DOCTYPE html>
<html>
<head><title>Fund Report Chat</title></head>
<body style="font-family: system-ui; max-width: 700px; margin: 40px auto;">
  <h2>Fund Performance Chat</h2>
  <div id="log" style="border:1px solid #ccc; padding:12px; height:400px; overflow-y:auto;"></div>
  <input id="input" style="width:80%;" placeholder="Ask about a fund's performance..." />
  <button onclick="send()">Send</button>

<script>
function send() {
  const input = document.getElementById('input');
  const log = document.getElementById('log');
  const msg = input.value;
  log.innerHTML += `<p><b>You:</b> ${msg}</p>`;
  input.value = '';

  const evt = new EventSource(`/api/chat/stream?message=${encodeURIComponent(msg)}`);
  let botLine = document.createElement('p');
  botLine.innerHTML = '<b>Bot:</b> <span></span>';
  log.appendChild(botLine);
  const span = botLine.querySelector('span');

  evt.onmessage = (e) => { span.textContent += e.data; log.scrollTop = log.scrollHeight; };
  evt.onerror = () => evt.close();
}
</script>
</body>
</html>
```

Because Spring Boot serves `src/main/resources/static/*` automatically, this needs no controller — it's available at `http://localhost:8080/index.html` once the app is running.

Next: [06 — Events & Module Boundary Tests](06-events-and-testing.md).
