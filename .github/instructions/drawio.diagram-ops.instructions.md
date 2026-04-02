---
description: "Draw.io diagram operations — create, edit, export, and validate architecture diagrams via MCP"
applyTo: "**"
---

# Draw.io Diagram Operations

When Draw.io MCP tools are available, use them for:
- Creating new `.drawio` architecture diagrams from natural-language descriptions
- Editing existing diagrams (add, remove, or reposition elements and connections)
- Exporting diagrams to `.svg` or `.png` for documentation
- Validating diagram structure and completeness

## Workflow

1. **Check for existing diagrams** — before creating a new diagram, look for existing `.drawio` files in the repository that may already cover the topic.
2. **Create with intent** — provide a clear description of the system, components, and relationships. Specify the shape library when cloud-specific icons are needed.
3. **Validate structure** — after generating or modifying a diagram, verify that all nodes are labelled, connectors have direction, and groups are semantically correct.
4. **Export when needed** — generate `.svg` or `.png` exports for use in documentation or pull requests.

## Operating Rules

- Prefer `.drawio` as the primary output format — it is editable in Draw.io desktop/web and VS Code (`hediet.vscode-drawio`).
- Do not present Draw.io behavior as package-owned functionality; this package delegates to the Draw.io MCP server.
- Default to generic shapes unless the user specifies a cloud provider or diagram type that requires a specific shape library.
- Place output files alongside related documentation or in a `docs/diagrams/` directory unless the user specifies a different path.
- When a diagram task falls outside Draw.io's capabilities, suggest alternative approaches (Mermaid for simple text-based diagrams, Lucidchart for collaborative editing).

## Validation Rules

After creating or modifying a diagram, check:
- Every node has a visible label.
- Every connector has a defined direction (source → target).
- Grouped elements (resource groups, VPCs, subnets) are semantically correct — child elements belong to the parent group.
- Legend or metadata is present for non-obvious icon meanings.
- The file is well-formed `.drawio` XML.

## Lucidchart Interop

Teams using Lucidchart can import agent-generated `.drawio` files via File → Import. Note that some Draw.io-specific styles may not transfer perfectly — prefer standard shapes and connectors for maximum portability.

Load the **drawio.diagram-ops** skill for detailed guidance on Draw.io XML format, shape libraries, layout strategies, and export options.
