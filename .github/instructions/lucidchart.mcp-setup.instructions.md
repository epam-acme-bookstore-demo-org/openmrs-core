---
description: "Lucidchart MCP server connection and authentication setup"
applyTo: "**"
---

# Lucidchart MCP Setup

## Server Endpoint

The Lucid MCP server is available at `https://mcp.lucid.app/mcp` and uses HTTP transport.

After running `apm install` for this package, the MCP dependency is materialized in the local runtime. Verify the Lucid MCP tools are available before attempting diagram operations.

## Authentication

Lucidchart MCP requires workspace admin approval through the Lucid Marketplace:

1. A workspace administrator must approve the Lucid MCP server integration in the Lucid Marketplace.
2. Once approved, authentication is handled through the MCP connection flow — no separate API token is needed in most configurations.
3. If the MCP tools are unavailable or return authorization errors, confirm that the workspace admin has completed the Marketplace approval step.

## Pre-flight Checks

Before any Lucidchart operation:
- Confirm Lucid MCP tools are available in the current session.
- If tools are missing, verify `apm install` has been run and the workspace admin has approved the integration.
- If authorization errors occur, stop and guide the user to complete the Lucid Marketplace approval flow before retrying.

Do not attempt diagram reads or writes until connectivity and authorization are confirmed.
