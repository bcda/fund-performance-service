# 09 — Connecting Claude Desktop to Your MCP Server

Your `mcpserver` module exposes a real MCP endpoint at `http://localhost:8080/mcp` (streamable HTTP). Claude Desktop can connect to it as an additional client alongside your own chat window — meaning you can ask Claude Desktop itself about your ingested fund reports.

There are two ways to do this, and picking the right one for your setup matters — Claude Desktop's two connection paths have different requirements.

## 9.1 Understand the two paths first

| | Custom Connector (Settings → Connectors) | `claude_desktop_config.json` |
|---|---|---|
| Transport | Streamable HTTP directly | **stdio only** — it does not read a `url` field |
| Best for | Remote HTTPS servers, often with OAuth | Local processes Claude Desktop spawns itself |
| Your local HTTP server | Works, but Claude may reject plain-HTTP `localhost` URLs depending on origin/CORS policy | Needs a **stdio bridge** (`mcp-remote`) since your server speaks HTTP, not stdio |

For a local streamable-HTTP server like yours, the most reliable path today is the **`mcp-remote` bridge** in `claude_desktop_config.json` — it works regardless of the Connectors UI's origin restrictions, and it's what most local-MCP-over-HTTP setups use in practice.

## 9.2 Prerequisite: Node.js on the Windows side

`mcp-remote` runs via `npx`, so Claude Desktop (a Windows app) needs Node available on Windows — not just inside WSL. Install it from [nodejs.org](https://nodejs.org) if you don't already have it, then confirm from PowerShell:
```powershell
node -v
npx -v
```

## 9.3 Confirm the WSL → Windows path works first

Before touching Claude Desktop's config, confirm your MCP endpoint is reachable **from Windows**, not just from inside WSL (this is the WSL networking check from page 07.5):
```powershell
curl http://localhost:8080/mcp
```
If this fails from Windows, fix WSL networking (page 07.5) before proceeding — the config below will otherwise fail in a confusing way that looks like a Claude Desktop problem.

## 9.4 Edit the config file

Location:
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- Open via Claude Desktop itself: Settings → Developer → Edit Config (this creates the file if it doesn't exist)

Add your server:
```json
{
  "mcpServers": {
    "fund-reports": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8080/mcp",
        "--transport", "http-only"
      ]
    }
  }
}
```

`--transport http-only` tells `mcp-remote` your server uses plain Streamable HTTP rather than the older SSE-based transport — matches what `spring-ai-starter-mcp-server-webflux` serves.

> If your app is only reachable over HTTPS with a self-signed cert (not the default setup here, since everything's on plain `localhost` HTTP), you'd add `"env": { "NODE_TLS_REJECT_UNAUTHORIZED": "0" } }` — skip this for the default setup above.

## 9.5 Restart Claude Desktop — fully

Editing the config file requires a full restart, not just closing the window:
- Quit Claude Desktop completely (system tray → Quit, not just the X button)
- Reopen it

## 9.6 Verify the connection

In Claude Desktop: click the "+" in the chat box → **Connectors**, or check **Settings → Developer** for connection status. You should see `fund-reports` listed with `searchFundDocuments` and `listIngestedFunds` as available tools.

If it's not showing up, check the logs:
- `%APPDATA%\Claude\logs\mcp-server-fund-reports.log` — this captures `mcp-remote`'s stderr output, which includes connection failures to your Spring app

## 9.7 Test it

Start a new chat in Claude Desktop and ask something that requires the tool, e.g.:
> "Using the fund-reports tools, what performance data do you have on the Vanguard 500 Index Fund?"

Claude Desktop will prompt you to approve the tool call the first time (MCP tool calls always require explicit approval in Claude Desktop) — approve it, and you should see the same retrieved chunks your own chat window would get, now answered by Claude Desktop instead of your local Qwen2.5 model.

## 9.8 Why this is a good validation step, not just a nice-to-have

If Claude Desktop can call your tools correctly but your own chat window (page 08) gives worse/ungrounded answers, that tells you precisely where the problem is: not retrieval (Claude Desktop proves the MCP server + Chroma pipeline works), but your local model's tool-calling behavior or your `ChatClient` prompt/config. This is a genuinely useful debugging asset to have, beyond just being a fun demo.

Next: [10 — Troubleshooting](10-troubleshooting.md).
