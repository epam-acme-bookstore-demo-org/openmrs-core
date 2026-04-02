---
description: "Draw.io MCP server connection and configuration setup"
applyTo: "**"
---

# Draw.io MCP Setup

## Server Options

Draw.io MCP servers use stdio transport. The default configuration uses the official server:

```
npx @drawio/mcp
```

Community servers exist for cloud-provider-specific shape libraries (e.g., `lilepeeps/Azure-DrawIO-MCP` for bundled Azure icons via Docker). These are optional extensions — the official server supports all built-in shape libraries.

## Prerequisites

- **Node.js** (v18+) for the official `npx @drawio/mcp` server.
- **Docker** (optional) for community servers that ship as container images.
- **VS Code Draw.io extension** (`hediet.vscode-drawio`) for in-editor `.drawio` file preview and editing.

## VS Code MCP Configuration

After running `apm install` for this package, the MCP dependency is materialized in the local runtime. To configure manually, add the server to `.vscode/mcp.json`:

```json
{
  "servers": {
    "drawio": {
      "type": "stdio",
      "command": "npx",
      "args": ["@drawio/mcp"]
    }
  }
}
```

For a Docker-based community server (e.g., Azure-specific):

```json
{
  "servers": {
    "drawio-azure": {
      "type": "stdio",
      "command": "docker",
      "args": ["run", "-i", "--rm", "lilepeeps/azure-drawio-mcp"]
    }
  }
}
```

## Claude Desktop Configuration

Add the server entry to the Claude Desktop MCP settings file:

```json
{
  "mcpServers": {
    "drawio": {
      "command": "npx",
      "args": ["@drawio/mcp"]
    }
  }
}
```

## Cloud-Provider Shape Libraries

The official Draw.io MCP server includes built-in shape libraries. Cloud-specific icon packs are opt-in:

- **Azure** — enable the Azure shape library or use a community server with bundled Azure2 SVG icons.
- **AWS** — enable the AWS shape library for AWS Architecture icons.
- **GCP** — enable the GCP shape library for Google Cloud icons.

These libraries are activated per diagram, not globally. Instructions for activation are in the **drawio.diagram-ops** skill.

## Pre-flight Checks

Before any Draw.io operation:
- Confirm Draw.io MCP tools are available in the current session.
- If tools are missing, verify `apm install` has been run and the MCP server starts without errors.
- Test connectivity by requesting available tool capabilities from the server.

Do not attempt diagram creation or edits until the MCP server is confirmed running.
