---
name: quality-assurance
description: Quality standards for deterministic behavior, regression safety, and release confidence
license: MIT
---

# Quality Assurance

Use this skill when introducing or modifying behavior across code, config, or automation.

## QA goals

1. Correctness: outputs and behavior match expected contracts.
2. Regression safety: changed paths are covered by automated tests.
3. Determinism: results are stable for equivalent inputs.
4. Operational safety: failures are actionable and diagnosable.

## Test strategy

1. Unit tests for business logic and edge cases.
2. Integration tests for wiring and end-to-end flows.
3. Contract/schema validation for output compatibility.

## Quality gates

- Define acceptance criteria before implementation.
- Add or update tests for changed behavior.
- Run focused checks first, then full validation suite.
- Document exceptions and rationale for any reduced coverage.

## Review checklist

- Are success and failure paths covered?
- Are tests deterministic (no flaky time/network assumptions)?
- Are output changes documented and validated?
- Are open risks and follow-up actions explicit?
