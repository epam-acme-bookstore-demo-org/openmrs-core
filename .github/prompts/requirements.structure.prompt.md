---
description: Structure a stakeholder transcript into Jira Epics and Stories with acceptance criteria
argument-hint: "Provide the stakeholder transcript and project context"
mcp: [atlassian]
---

# Structure Requirements from Transcript

A stakeholder conversation or meeting transcript needs to be structured into actionable Jira issues.

## Transcript

${input:transcript}

## Project Context

${input:project_context}

## Instructions

1. Read the project context to understand existing epics, components, and conventions.
2. Analyze the transcript and identify distinct requirements, feature requests, and action items.
3. Group related requirements under Epics (create new Epics if no existing one fits).
4. For each requirement, create a Story with:
   - Clear summary and description
   - Testable acceptance criteria (Given/When/Then or checkbox format)
   - Appropriate priority, labels, and components
5. Link related stories and flag dependencies.
6. List any ambiguities or open questions that need stakeholder clarification.
7. Present the complete structured output for review before creating issues in Jira.
