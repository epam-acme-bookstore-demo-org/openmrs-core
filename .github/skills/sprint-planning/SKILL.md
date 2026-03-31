---
name: sprint-planning
description: Structured sprint/phase planning — turns design documents into implementation plans, task breakdowns, and GitHub Issues ready for autonomous execution
license: MIT
---

# Sprint / Phase Planning

Use this skill to turn a set of design or specification documents into a structured, execution-ready implementation plan with discrete, independently-workable tasks and linked GitHub Issues.

## When to use

- Starting a new phase or sprint from design documentation.
- Decomposing an epic into independently-workable tasks for specialist agents.
- Creating a traceable link between design intent, GitHub Issues, and deliverable artifacts.

## Workflow

### Step 1 — Read and Internalize

Read **all** design documents before producing any plan output. Building a complete mental model first avoids rework when later documents contradict earlier assumptions.

- Identify the system boundary, constraints, and phasing strategy.
- Note cross-component boundaries (e.g., changes that span an existing component and a new one).
- Map the team composition: which specialist agents are available and what domains they own.

### Step 2 — Produce the Implementation Plan

Emit an `implementation-plan.md` artifact that:

- Groups work into phased workstreams (Phase 0–N).
- Identifies dependencies between workstreams.
- Routes each workstream to the appropriate specialist (TypeScript, Infrastructure, GitHub Actions, Technical Writing, etc.).
- Defines per-phase acceptance criteria.
- Aligns CI/CD strategy with phase progression.
- Flags cross-component boundary changes explicitly.
- Includes a phase-transition gate: criteria that confirm Phase N is done before planning Phase N+1.

### Step 3 — Decompose the Target Phase into Tasks

Emit a `phase-<N>-tasks.md` artifact that:

- Contains **10–15 tasks max** (too many = too granular; too few = too coarse).
- Each task specifies: Goal, Deliverables (specific file paths), References (back to design docs), Acceptance Criteria (testable/automatable), Time estimate (1–2 day granularity), and Owner (specialist agent role).
- Includes a **dependency graph** (DAG) with critical path and parallelizable streams identified.
- Confirms the DAG is acyclic before finalizing.

### Step 4 — Create GitHub Issues

Using GitHub MCP tools:

1. Create a **parent phase issue** linked as a sub-issue of the epic.
2. Create **individual task issues** (one per task), each linked as a sub-issue of the parent phase issue.
3. Assign all issues to the project board and milestone.
4. Issue body must match the task detail from the `phase-<N>-tasks.md` artifact.

### Step 5 — Review Gate

Before handing off to specialist agents, request a Copilot code review of the implementation plan and task breakdown. Address any blocking concerns raised before execution begins.

## Quality constraints

| Constraint | Standard |
|---|---|
| Acceptance criteria | Must be testable — can a CI pipeline verify it? |
| Deliverables | Must reference specific file paths — no vague "update the code" |
| Dependency graph | Must be a DAG — validate for cycles before finalizing |
| Time estimates | Conservative — 1–2 day granularity per task |
| Task count per phase | 10–15 maximum |
| Cross-boundary changes | Flag explicitly in the plan |

## Anti-patterns to avoid

- **Partial read** — Never produce a plan before reading all design docs.
- **Vague deliverables** — "Implement the service" is not a deliverable; `src/services/spider.ts` is.
- **Cyclic dependencies** — Draw the DAG and verify before finalizing.
- **Non-testable criteria** — If you cannot write a command to verify it, it is not a criterion.
- **Skipping the review gate** — The review step catches blocking issues before they propagate to execution.

## Output templates

### Implementation Plan (`implementation-plan.md`)

```markdown
# Implementation Plan: <project / initiative>

## Overview
<one-paragraph summary of the system and planning scope>

## Phase Roadmap

| Phase | Name | Workstreams | Owner(s) | Status |
|---|---|---|---|---|
| 0 | Foundation | ... | ... | planning |

## Phase <N>: <Name>

### Workstreams
- **<Workstream A>** — <description> — Owner: <specialist>
- **<Workstream B>** — <description> — Owner: <specialist>

### Dependencies
- <Workstream B> depends on <Workstream A>

### CI/CD Alignment
<how CI/CD gates change as this phase completes>

### Acceptance Criteria
- [ ] <criterion>

### Phase-Transition Gate
Conditions that confirm this phase is complete before planning the next:
- [ ] All acceptance criteria verified in CI.
- [ ] No open blocking issues.
- [ ] Review gate passed.

## Cross-Boundary Flags
- <component A> ↔ <component B>: <nature of change>
```

### Task Breakdown (`phase-<N>-tasks.md`)

```markdown
# Phase <N> Task Breakdown: <phase name>

## Dependency Graph

<ASCII or Mermaid DAG — validate acyclicity before publishing>

### Critical Path
<list of tasks on the critical path>

### Parallelizable Streams
- Stream A: tasks <X, Y>
- Stream B: tasks <X, Z>

## Tasks

### <ID>-01: <Task Title>

**Goal**: <what outcome this task produces>

**Owner**: <specialist agent role>

**Deliverables**:
- `path/to/file.ts`
- `path/to/file.test.ts`

**References**:
- `docs/01-vision.md` §<section>

**Acceptance Criteria**:
- [ ] `<command>` exits 0.
- [ ] `<file>` exists and exports `<symbol>`.

**Depends on**: <ID>-00 (or "none")

**Estimate**: <N> day(s)
```

### GitHub Issue Body (`task-issue.md`)

```markdown
## Goal
<paste from task breakdown>

## Deliverables
<paste from task breakdown>

## Acceptance Criteria
<paste from task breakdown>

## References
<paste from task breakdown>

## Dependencies
<paste from task breakdown>

## Estimate
<paste from task breakdown>

/cc @<tech-lead>
```

## Learnings from the field

- **Reading all design docs first** before producing any plan avoids rework when later documents contradict earlier assumptions.
- **Explicit dependency graphs** make parallelization obvious and prevent blockers during execution.
- **File-level deliverables** allow agents to execute tasks without scope ambiguity.
- **Testable acceptance criteria** mean every task can be validated by running a command.
- **Specialist routing in the plan** means the tech lead knows exactly which agent to delegate to.
- **Documentation-as-deliverable** — include docs and validation scripts as explicit tasks so they are not forgotten.
- **Estimation calibration** — human-day estimates are optimistic for agent execution; agents complete work substantially faster than human-day estimates suggest. Use estimates for relative sizing and dependency ordering, not calendar planning.
- **Risk and rollback** — identify per-task risk level and define a rollback criterion (when to abandon vs. push through).
