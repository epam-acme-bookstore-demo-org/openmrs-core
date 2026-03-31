---
name: cli-technical-documentation
description: Standards for complete, accurate, and maintainable CLI documentation
license: MIT
---

# CLI Technical Documentation

Use this skill when documenting CLI behavior.

## Coverage

- Quickstart and setup path.
- Command reference with options and examples.
- Troubleshooting and common error cases.
- Contributor updates when CLI behavior changes.

## Required quality rules

- Every documented command or subcommand includes at least one runnable example.
- Flags/options include defaults and constraints when applicable.
- Inputs/outputs, side effects, and exit behavior are explicit.
- Docs changes accompany user-facing CLI behavior changes.

## Maintenance workflow

1. Verify current CLI surface area from source.
2. Update canonical reference docs first.
3. Update quickstart/examples affected by behavior changes.
4. Validate examples and commands for correctness.

## Review checklist

- Can a new user run a complete flow from docs alone?
- Are examples copy/paste-ready and dependency-aware?
- Are deprecated or non-runnable commands clearly marked?
