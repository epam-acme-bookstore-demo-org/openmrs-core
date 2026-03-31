---
name: azure-naming
description: Azure resource naming rules, conventions, and validation workflow
license: MIT
---

# Azure Naming

Use this skill when defining or reviewing Azure resource names.

## Principles

- Follow provider-specific naming constraints.
- Keep naming deterministic and environment-aware.
- Include uniqueness strategy where global scope requires it.
- Validate names before deployment.

## Naming model

- Compose names using: resource type abbreviation + workload + environment + optional region + uniqueness suffix.
- Keep names lowercase and human-parsable where the provider permits.
- Avoid sensitive data in names.

## Validation checklist

- Resource-specific length/charset rules are satisfied.
- Global resources include collision-safe suffix strategy.
- Name generation is deterministic for equivalent inputs.
- Validation failures return actionable messages.
