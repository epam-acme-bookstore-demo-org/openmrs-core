---
description: Refine raw requirements into structured work items with acceptance criteria
argument-hint: "Provide the raw requirements to refine"
---

# Refine Requirements

Given the following raw requirements, refine them into structured, actionable work items.

## Raw Input

${input:raw_requirements}

## Instructions

1. Read and understand each requirement in the raw input.
2. For each requirement, produce a structured work item with:
   - **Summary**: one-sentence description.
   - **Acceptance Criteria**: testable checkboxes covering happy paths and edge cases.
   - **Affected Scope**: list of affected areas, modules, or files.
   - **Priority**: high / medium / low (based on context clues; flag if unclear).
   - **Open Questions**: anything ambiguous that needs human clarification.
3. Group related requirements when they share scope.
4. Flag any conflicts or overlaps between requirements.
