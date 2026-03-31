---
description: Perform a structured code review on a set of changes
argument-hint: "Provide the diff or file paths and related requirements"
---

# Code Review

Perform a structured technical review of the following changes.

## Changes

${input:diff_or_files}

## Requirements Being Addressed

${input:requirements}

## Review Checklist

1. **Correctness** — does the code do what the requirement asks?
2. **Edge cases** — are boundary conditions handled?
3. **Error handling** — are failures handled gracefully?
4. **Security** — any injection, auth, or data exposure risks?
5. **Conventions** — does the code follow project standards?
6. **Tests** — are changes covered by appropriate tests?
7. **Backward compatibility** — does this break existing behavior?
8. **Documentation** — are docs updated if behavior changed?

Produce a review summary with verdict, blocking issues, suggestions, and requirements adherence assessment.
