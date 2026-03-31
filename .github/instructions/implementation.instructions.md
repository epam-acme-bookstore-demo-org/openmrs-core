---
description: >
  Always-on standards for the Implementation Loop.
  Ensures deterministic, reviewable, and well-scoped code changes.
applyTo: "**"
---

# Implementation Loop Standards

## Planning

- Every implementation starts with a plan that maps requirements to concrete changes.
- Plans must identify affected files, dependencies, and validation steps.
- Infrastructure and application changes are planned separately but coordinated.

## Development

- Keep changes scoped and minimal — address only what the requirement asks for.
- Preserve backward compatibility unless explicitly changing behavior.
- Add or update tests for every code path touched.
- Run targeted validation (lint, test, build) before considering work complete.

## Delegation

- The Tech Lead creates the plan and delegates to specialists.
- Each delegation must include: goal, scope, acceptance criteria, and validation commands.
- Each specialist responds with: summary, changed files, validation results, risks, and open questions.
