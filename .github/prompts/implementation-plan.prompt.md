---
description: Create an implementation plan from refined requirements
argument-hint: "Provide the requirements and codebase context"
---

# Implementation Plan

Create a detailed implementation plan for the following requirements.

## Requirements

${input:requirements}

## Codebase Context

${input:codebase_context}

## Instructions

1. Analyze each requirement and identify the scope of changes needed.
2. For each change, specify:
   - **Task**: what needs to be done.
   - **Type**: application code | infrastructure | configuration | documentation.
   - **Affected files/modules**: specific paths.
   - **Dependencies**: tasks that must complete first.
   - **Validation**: commands to verify the change works.
   - **Estimated complexity**: low / medium / high.
3. Order tasks by dependency and priority.
4. Identify risks and flag anything that needs human decision.
5. Present the plan as a numbered task list ready for delegation.
