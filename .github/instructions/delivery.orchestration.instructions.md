---
description: >
  Always-on orchestration rules for the Delivery Loop.
  Defines phase sequence, human gates, context contracts, and scenario-aware execution.
applyTo: "**"
---

# Delivery Loop Orchestration

The Delivery Loop is the primary work cycle. Every piece of work — feature, fix, improvement — progresses through three phases in strict order. Skipping or reordering phases is not permitted.

## Phase sequence

```
Requirements ──► Implementation ──► Quality
     ▲                                  │
     └────────── feedback (rework) ─────┘
```

1. **Requirements** — Discover, refine, validate, and prioritize work items. Exit: structured, testable work items approved by stakeholders.
2. **Implementation** — Plan, implement, test, and document. Exit: working, tested code with updated documentation.
3. **Quality** — Review, validate, and gate. Exit: changes approved or sent back with actionable feedback.

## Human gates

Every phase transition requires human approval. Agents must:

1. Present the phase outcome with evidence (deliverables, test results, review findings).
2. Explicitly ask the human to approve moving to the next phase.
3. Wait for approval before proceeding. Never auto-advance.

If the Quality phase rejects changes, the cycle returns to the appropriate earlier phase (Requirements for scope issues, Implementation for code issues) with specific feedback. This is not a failure — it is the loop functioning correctly.

## Context contract

Before starting any phase:

1. **Read context** — identify and load relevant knowledge sources (business docs, technical docs, existing code, guidelines, work items).
2. **State what was read** — briefly list the context sources consulted so the human can verify completeness.

After completing any phase:

1. **Write context** — update all affected documentation, knowledge bases, and work items.
2. **State what was updated** — list the context changes made so the human can verify accuracy.

A phase that produces code but leaves documentation stale has not completed its context contract.

## Scenario awareness

Before starting a Delivery Loop cycle, identify the scenario profile that best fits the work:

- **Greenfield** — thin or empty context. Requirements phase is heavy. Context is built from scratch.
- **Brownfield** — rich existing context. Must read and reconcile before acting. Quality focus: don't break what works.
- **Modernisation** — dual context (as-is + to-be). Implementation gains an analyse-legacy-first rhythm. Quality must validate both old and new.

State the identified scenario profile at the start of the cycle. If the corresponding scenario package is installed (greenfield, brownfield, or modernisation), follow its delivery profile skill for detailed phase adaptation guidance. If no scenario package is installed, apply the profile descriptions above as baseline guidance.

## Rework rules

When the Quality phase sends changes back:

1. Route scope/requirements issues back to the **Requirements** phase.
2. Route implementation/code issues back to the **Implementation** phase.
3. Preserve all context from the current cycle — do not start from scratch.
4. Increment the rework counter. After 2 rework cycles on the same item, escalate to the human for a decision on how to proceed.
