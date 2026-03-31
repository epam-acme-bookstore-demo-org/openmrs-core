---
description: Plan a specific phase from an existing implementation plan — produce a task breakdown with dependency graph and create GitHub Issues
argument-hint: "Provide the implementation plan path, phase number, phase issue URL, and team composition"
---

# Plan Phase

Decompose a specific phase from an existing implementation plan into a discrete task breakdown, then create GitHub Issues.

## Inputs

**Implementation Plan**: ${input:implementation_plan}

**Phase to Plan**: ${input:phase}

**Parent Phase Issue URL**: ${input:parent_issue_url}

**Team Composition** (available specialist agents): ${input:team_composition}

**Project Board**: ${input:project_board}

**Milestone**: ${input:milestone}

## Instructions

### 1. Read the Implementation Plan

Read the implementation plan to understand the full phasing strategy, dependencies between phases, and the workstreams within the target phase.

Confirm:
- Which workstreams belong to the target phase.
- Which specialist owns each workstream.
- What the acceptance criteria and phase-transition gate are for this phase.
- Which prior phases must be complete before this phase begins (confirm they are done).

### 2. Decompose the Phase into Tasks

Emit a Markdown artifact (`phase-<N>-tasks.md`) with **10–15 discrete tasks**. Each task must include:

- **Goal** — what outcome this task produces.
- **Owner** — which specialist agent role is responsible.
- **Deliverables** — specific file paths (no vague descriptions).
- **References** — back to relevant design document or implementation plan sections.
- **Acceptance Criteria** — testable checkboxes (CI-verifiable commands).
- **Depends on** — task IDs that must complete first (or "none").
- **Estimate** — in days (1–2 day granularity).

Draw a **dependency graph (DAG)** across all tasks. Identify the critical path and parallelizable streams. Validate that the DAG is acyclic before finalizing.

### 3. Review Gate

Present the task breakdown for review before creating issues. Address any blocking concerns.

### 4. Create GitHub Issues

Using GitHub MCP tools:

1. For each task, create a **task issue** body matching the task detail from the breakdown.
2. Link each task issue as a sub-issue of the parent phase issue provided.
3. Assign all issues to the project board and milestone provided as separate inputs.
4. Return a summary table: issue number, title, URL for each created issue.
