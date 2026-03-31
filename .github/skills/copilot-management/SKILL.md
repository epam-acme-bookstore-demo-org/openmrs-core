---
name: copilot-management
description: Workflow for designing and maintaining Copilot skills, agents, instructions, and prompts
license: MIT
---

# Copilot Management

Use this skill when creating or updating Copilot configuration.

## Workflow (ordered)

1. Discovery: inventory existing artifacts and reuse what already exists.
2. Selection: choose the right artifact type based on intent.
3. Implementation: apply minimal, scoped changes.
4. Validation: compile and verify artifact integrity.

## Decision matrix

- `skills`: reusable procedures and domain workflows.
- `agents`: persona + tool boundaries for recurring task classes.
- `instructions`: always-on repository standards.
- `prompts`: on-demand guided workflows.

## Validation checklist

- Frontmatter is valid and minimal.
- Artifact scope is clear and non-overlapping.
- No unrelated product code was changed.
- Installation/compile flows still pass.
