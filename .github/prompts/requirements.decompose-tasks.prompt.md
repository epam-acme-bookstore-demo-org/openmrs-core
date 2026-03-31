---
description: Decompose a phase or workstream description into discrete, independently-workable tasks with a validated dependency graph and critical path
argument-hint: "Provide the phase or workstream description and team composition"
---

# Decompose Tasks

Break a phase or workstream description into discrete, independently-workable tasks with a validated dependency graph.

## Inputs

**Phase / Workstream Description**: ${input:phase_description}

**Phase ID** (e.g., `0`, `1`, `2` — used to prefix task IDs as `P<phase_id>-<nn>`): ${input:phase_id}

**Design Document References**: ${input:design_refs}

**Team Composition** (available specialist agents): ${input:team_composition}

## Instructions

### 1. Analyze the Scope

Read the phase or workstream description and all referenced design documents. Identify:

- The deliverable artifacts (specific files, modules, or infrastructure resources).
- The testable outcomes for the phase as a whole.
- The specialist domains involved.

### 2. Decompose into Tasks

Produce a list of **10–15 discrete tasks**. Each task must be:

- **Independently workable** — a specialist can begin it once its dependencies are satisfied, without waiting for other tasks in the same stream.
- **Scoped to 1–2 days** of work.

Each task entry must include:

- **ID**: sequential identifier using the phase ID input (e.g., `P${input:phase_id}-01`, `P${input:phase_id}-02`).
- **Title**: one-line description.
- **Goal**: the technical outcome this task produces.
- **Owner**: specialist agent role (TypeScript, Infrastructure, GitHub Actions, Technical Writing, etc.).
- **Deliverables**: specific file paths — no vague descriptions.
- **References**: section links back to design docs provided.
- **Acceptance Criteria**: testable checkboxes. Each criterion must be verifiable by a CI command.
- **Depends on**: IDs of tasks that must complete first, or "none".
- **Estimate**: in days.
- **Risk**: low / medium / high, with a one-line rationale.
- **Rollback criterion**: the condition under which this task should be abandoned rather than pushed through.

### 3. Validate the Dependency Graph

Draw the dependency graph (DAG) in Mermaid or ASCII format. Then:

1. Verify it is **acyclic** — if any cycle is found, restructure tasks to remove it.
2. Identify the **critical path** (longest path through the DAG).
3. Identify **parallelizable streams** (sets of tasks with no mutual dependencies).

Present the DAG alongside the task list.

### 4. Quality Check

Before emitting the final output, verify:

- [ ] Task count is between 10 and 15.
- [ ] Every deliverable references a specific file path.
- [ ] Every acceptance criterion is verifiable by a CI command.
- [ ] The DAG is acyclic.
- [ ] Each task has an assigned owner.
- [ ] Cross-component boundary changes are flagged.
