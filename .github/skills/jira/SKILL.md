---
name: jira
description: Jira issue structuring conventions and project-context usage for backlog creation workflows
license: MIT
---

# Jira

Use this skill when creating, reviewing, or updating Jira work items from stakeholder input, technical analysis, or delivery planning.

## What this skill covers

- Jira issue hierarchy and when to use Epic, Story, Task, and Bug
- Acceptance criteria standards for review-ready backlog items
- Metadata expectations such as priority, labels, components, and links
- How to use project-specific Jira context alongside generic conventions

## Issue hierarchy

1. Epic: Groups related capabilities or a broader business objective.
2. Story: Describes a user-facing slice of value with testable acceptance criteria.
3. Task: Captures technical or operational work that supports delivery but does not directly describe user value.
4. Bug: Captures a defect with reproduction steps, expected behavior, and actual behavior.

## Acceptance criteria standards

- Write acceptance criteria as testable Given/When/Then statements or checkbox items.
- Ensure each criterion is independently verifiable.
- Cover happy path, edge cases, and error handling where relevant.
- Keep scope explicit; avoid hidden assumptions.

## Metadata conventions

- Set priority deliberately based on business impact and delivery timing.
- Assign components that match the owning product or technical area.
- Add labels that help triage, reporting, and filtering.
- Link related work using blocks, is-blocked-by, or relates-to when dependencies exist.
- Flag ambiguities and open questions before creating delivery-ready items.

## Project context usage

Use a local Jira project context file when a workflow depends on project-specific metadata such as:

- Project key, board, and issue types
- Allowed priorities
- Component taxonomy
- Default labels
- Existing epics or release/sprint anchors

Treat that local context file as project metadata, not as the source of generic Jira conventions.

## Workflow

1. Read the project-specific Jira context first.
2. Apply these generic Jira conventions to structure the work.
3. Check for duplicates and related issues before creation.
4. Present review-ready output before creating or updating issues when human approval is expected.

## MCP usage

When Atlassian MCP tools are available, prefer them for project metadata lookup, issue creation, issue updates, and workflow transitions.