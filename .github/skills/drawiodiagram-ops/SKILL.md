---
name: drawio-diagram-ops
description: Draw.io diagram operations — XML format, shape libraries, layout strategies, and export via Draw.io MCP
license: MIT
---

# Draw.io Diagram Operations

Use this skill when the task involves creating, editing, or exporting architecture diagrams through Draw.io's MCP integration.

Do not treat this package as an implementation of Draw.io features. It wires agents to Draw.io MCP servers so they can produce fully editable `.drawio` diagram files from the IDE.

## Setup And Access First

Before attempting Draw.io actions, verify the runtime setup:
- `apm install` has been run for the package so the MCP dependency is materialized in the local runtime.
- The Draw.io MCP server starts without errors (`npx @drawio/mcp` or equivalent Docker command).
- For in-editor preview, the VS Code extension `hediet.vscode-drawio` is installed.

If Draw.io MCP tools are unavailable, resolve setup first instead of attempting diagram operations.

## When To Use This Package

Use Draw.io MCP workflows for:
- Generating architecture diagrams as editable `.drawio` files
- Creating cloud infrastructure diagrams with provider-specific icon libraries
- Producing flowcharts, network diagrams, UML diagrams, and ERDs
- Editing existing `.drawio` files programmatically (add/remove/move elements)
- Exporting diagrams to `.svg` or `.png` for documentation

Avoid using it for:
- Tasks that do not involve visual diagrams
- Simple diagrams where Mermaid in Markdown is sufficient
- Collaborative real-time editing (use Lucidchart instead)

## Draw.io XML Format Basics

Draw.io files use an XML format based on mxGraph:

- **`<mxGraphModel>`** — root element, contains the diagram canvas properties.
- **`<root>`** — container for all diagram cells.
- **`<mxCell>`** — fundamental element representing nodes, edges, and groups.
  - `id` — unique identifier for the cell.
  - `value` — display label (supports HTML).
  - `style` — semicolon-separated style properties (shape, fillColor, strokeColor, etc.).
  - `vertex="1"` — marks the cell as a shape/node.
  - `edge="1"` — marks the cell as a connector/edge.
  - `parent` — ID of the parent cell (use for grouping).
  - `source` / `target` — IDs of connected cells (for edges).
- **`<mxGeometry>`** — position and size of the cell (`x`, `y`, `width`, `height`).

Every diagram has two default cells: `id="0"` (root) and `id="1"` (default layer, parent of all top-level elements).

## Shape Libraries

Draw.io supports multiple shape libraries. The core library (general shapes, flowchart, UML) is always available. Cloud-provider and specialty libraries are opt-in.

### Built-in Libraries
- **General** — rectangles, circles, diamonds, arrows, text blocks.
- **Flowchart** — process, decision, terminator, document, data shapes.
- **UML** — class, interface, component, sequence, state, activity shapes.
- **Network** — server, database, cloud, firewall, router, switch shapes.
- **Entity Relationship** — entity, attribute, relationship shapes.

### Cloud-Provider Libraries (Opt-in)
- **Azure** — Azure2 icon set. Style prefix: `sketch=0;aspect=fixed;pointerEvents=1;shadow=0;dashed=0;html=1;strokeColor=none;labelPosition=center;verticalLabelPosition=bottom;verticalAlign=top;align=center;shape=mxgraph.azure`.
- **AWS** — AWS Architecture icons. Style prefix: `shape=mxgraph.aws4`.
- **GCP** — Google Cloud icons. Style prefix: `shape=mxgraph.gcp2`.

To use a cloud-provider library, include the library prefix in the cell style and reference the specific icon name (e.g., `shape=mxgraph.azure.app_service` for Azure App Service).

## Auto-Layout Strategies

When generating diagrams, choose the layout that best represents the system:

- **Hierarchical (top-down)** — best for layered architectures (presentation → business → data). Set child cells at increasing `y` positions.
- **Hierarchical (left-right)** — best for data-flow and pipeline diagrams. Set child cells at increasing `x` positions.
- **Tree** — best for organizational or dependency hierarchies. Use a single root node with branching children.
- **Organic** — best for complex networks with many cross-connections. Let Draw.io auto-layout distribute nodes.

General layout guidelines:
- Maintain consistent spacing (80–120px between nodes).
- Align related elements horizontally or vertically.
- Flow direction should be visually obvious (top-to-bottom or left-to-right).

## Grouping And Layering

Use groups to represent logical boundaries:

- **Resource groups** (Azure), **VPCs** (AWS), **Projects** (GCP) — create a parent `mxCell` with `vertex="1"`, `style="group"`, and a visible label. Add child cells with `parent` set to the group's ID.
- **Subnets / availability zones** — nest inside the parent group as a second level of grouping.
- Use distinct fill colors or dashed borders to differentiate group types visually.
- Ensure child elements are positioned within the parent's bounding box.

## Connectors

- **Solid lines** — synchronous calls, direct dependencies.
- **Dashed lines** — asynchronous communication, event-driven flows.
- **Arrow direction** — always source → target, matching the data or control flow.
- **Labels on connectors** — describe the protocol, data type, or action (e.g., "HTTPS", "Event Grid", "REST API").
- Set `edge="1"` and define `source` and `target` cell IDs on the `mxCell`.

## Legend And Metadata

- Include a legend when the diagram uses non-obvious icons or color coding.
- Add a title text block at the top of the diagram.
- Use tooltips or HTML labels for additional metadata on nodes when needed.
- For architecture decision records, link to the relevant ADR document in the node's tooltip or label.

## Export Options

- **`.drawio`** — primary format. Editable in Draw.io desktop, web, and VS Code. Version-control friendly.
- **`.svg`** — vector export for documentation. Embed in Markdown or HTML. Preserves text searchability.
- **`.png`** — raster export for presentations or image-only contexts. Use when vector is not supported.

Prefer `.drawio` as the source of truth and generate `.svg` or `.png` exports on demand.

## Lucidchart Import Path

Teams using Lucidchart can consume agent-generated diagrams via File → Import → select the `.drawio` file. Standard shapes and connectors transfer reliably. Cloud-specific icon mappings may require manual adjustment after import.
