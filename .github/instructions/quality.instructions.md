---
description: >
  Always-on quality standards for code changes.
  Ensures correctness, regression safety, and deterministic behavior.
applyTo: "**"
---

# Quality Standards

## Correctness

- Every change must be validated with automated tests before merging.
- Test coverage must include happy paths, edge cases, and error scenarios.
- Existing tests must pass — regressions are blocking.

## Review gates

- **Business adherence**: changes must trace back to accepted requirements.
- **Technical review**: code must follow project conventions and be maintainable.
- **Security**: no new vulnerabilities introduced (CodeQL or equivalent).

## Deterministic behavior

- Given the same input and context, agents must produce the same output.
- Avoid non-deterministic constructs (random values, timestamps) in generated code unless explicitly required.
- Prefer explicit over implicit — make assumptions visible.
