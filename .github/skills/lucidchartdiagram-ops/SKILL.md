---
name: lucidchart-diagram-ops
description: Lucidchart diagram operations — search, retrieval, analysis, and generation patterns via Lucid MCP
license: MIT
---

# Lucidchart Diagram Operations

Use this skill when the task involves searching, retrieving, analyzing, or generating diagrams through Lucidchart's MCP integration.

Do not treat this package as an implementation of Lucidchart features. It wires agents to Lucid Software's official MCP server so they can use upstream diagram workflows from the IDE.

## Setup And Access First

Before attempting Lucidchart actions, verify the runtime and authentication setup:
- `apm install` has been run for the package so the MCP dependency is materialized in the local runtime.
- The workspace administrator has approved the Lucid MCP server integration via the Lucid Marketplace.

If Lucid MCP tools are unavailable or return authorization errors, resolve setup first instead of attempting diagram operations.

## When To Use This Package

Use Lucidchart MCP workflows for:
- Searching existing diagrams by name, content, or natural-language description
- Retrieving diagram structure, elements, and metadata
- Analyzing relationships, flows, and layout of existing diagrams
- Generating new diagrams from textual descriptions or requirements
- Modifying existing diagram content, elements, or structure

Avoid using it for:
- Tasks that do not involve Lucidchart diagrams
- Generating diagrams when a simpler text-based format (like Mermaid in Markdown) is sufficient
- Speculating about Lucidchart internals that the MCP server does not expose

## Workflow Guidance

### 1. Search For Existing Diagrams

Before creating a new diagram, search for existing ones that may already cover the topic.

Use natural-language queries that describe the diagram's subject, purpose, or content. Be specific about the domain or system area to narrow results.

### 2. Retrieve And Analyze Diagrams

When working with an existing diagram:
- Retrieve the full diagram structure to understand its current state
- Identify the elements, connections, and layout before proposing changes
- Note any naming conventions or organizational patterns already in use

### 3. Generate New Diagrams

When creating a new diagram:
- Provide a clear description of the system, flow, or concept to visualize
- Specify the diagram type when relevant (flowchart, sequence, architecture, ERD, etc.)
- Include the key elements and their relationships
- Describe the intended audience and level of detail

### 4. Modify Existing Diagrams

When updating an existing diagram:
- Retrieve the current diagram state first
- Describe changes in terms of what to add, remove, or reorganize
- Preserve existing structure and conventions unless the user explicitly asks for reorganization

## Diagram Type Guidance

Common diagram types and when to use them:
- **Architecture diagrams** — system components, services, and their interactions
- **Flowcharts** — process flows, decision trees, and workflow steps
- **Sequence diagrams** — time-ordered interactions between actors or services
- **ERD (Entity-Relationship)** — data models and database schema relationships
- **Network diagrams** — infrastructure topology and connectivity
- **Org charts** — team structure and reporting relationships

## Operating Notes

This package targets Lucid Software's official MCP server at `https://mcp.lucid.app/mcp` (beta since November 2025).

Keep package guidance focused on stable workflows the server supports: diagram search, retrieval, content analysis, and generation.
