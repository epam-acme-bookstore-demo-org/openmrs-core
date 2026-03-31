---
description: Full sprint planning workflow — read design docs, produce an implementation plan and task breakdown, then create GitHub Issues linked to the epic
argument-hint: "Provide the design doc paths (or glob), epic issue URL, target phase, and team composition"
---

# Sprint Planning

Run the full sprint / phase planning workflow from design documents to GitHub Issues.

## Inputs

**Design Documents**: ${input:design_docs}

**Epic Issue URL**: ${input:epic_issue_url}

**Target Phase**: ${input:target_phase}

**Team Composition** (available specialist agents): ${input:team_composition}

**Project Board**: ${input:project_board}

**Milestone**: ${input:milestone}

## Instructions

Follow the sprint-planning skill workflow exactly.

### 1. Read and Internalize

Read every design document listed in the inputs sequentially. Do not produce any plan output until all documents have been read.

- Build a complete mental model of the system, its constraints, and its phasing strategy.
- Identify cross-component boundaries (changes that span existing components and new ones).
- Map each workstream to the appropriate specialist from the team composition provided.

### 2. Produce the Implementation Plan

Emit a Markdown artifact (`implementation-plan.md`) that:

- Groups work into phased workstreams.
- Identifies dependencies between workstreams.
- Routes each workstream to the correct specialist.
- Defines per-phase acceptance criteria.
- Aligns CI/CD strategy with phasing progression.
- Flags cross-component boundary changes explicitly.
- Defines a phase-transition gate (conditions that confirm the phase is done).

Present this document for review before proceeding to task decomposition.

### 3. Decompose the Target Phase into Tasks

Emit a Markdown artifact (`phase-<N>-tasks.md`) that decomposes the target phase into 10–15 discrete tasks. Each task must include:

- **Goal** — what outcome this task produces.
- **Owner** — which specialist agent role is responsible.
- **Deliverables** — specific file paths (no vague descriptions).
- **References** — back to the relevant design document sections.
- **Acceptance Criteria** — testable checkboxes (CI-verifiable commands).
- **Depends on** — task IDs that must complete first (or "none").
- **Estimate** — in days (1–2 day granularity).

Draw a **dependency graph (DAG)** across all tasks. Identify the critical path and parallelizable streams. Validate that the DAG is acyclic before finalizing.

Present this document for review before creating issues.

### 4. Review Gate

Request a review of both the implementation plan and the task breakdown. Address any blocking concerns before creating GitHub Issues.

### 5. Create GitHub Issues

Using GitHub MCP tools:

1. Retrieve the epic issue to confirm its details.
2. Create a **parent phase issue** (title: `Phase <N>: <phase name>`) and link it as a sub-issue of the epic.
3. For each task in the task breakdown, create a **task issue** using the task-issue template from the sprint-planning skill, and link it as a sub-issue of the parent phase issue.
4. Assign all issues to the project board and milestone provided as separate inputs.
5. Return a summary table: issue number, title, URL for each created issue.
