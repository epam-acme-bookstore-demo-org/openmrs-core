---
description: >
  Always-on standards for knowledge management.
  Ensures documentation stays synchronized with code and feeds the RAG pipeline.
applyTo: "**"
---

# Knowledge Management Standards

## Documentation as code

- Documentation lives alongside code in Markdown files.
- When code changes, affected documentation must be updated in the same PR.
- Keep documentation concise, task-oriented, and actionable.

## Knowledge pipeline

- All information (requirements, architecture decisions, coding standards, business knowledge) is captured in Markdown.
- Markdown content feeds the RAG pipeline for agent grounding.
- Stale or incorrect knowledge must be flagged and corrected promptly.

## Traceability

- Architecture decisions must be recorded with context and rationale.
- Link documentation to the requirements or issues that drove the change.
