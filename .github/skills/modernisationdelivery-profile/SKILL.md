---
name: modernisation-delivery-profile
description: Modernisation scenario profile — phase adaptation guidance for transforming legacy systems with dual-context bridging
license: MIT
---

# Modernisation Delivery Profile

This profile applies when the Delivery Loop operates on a **legacy system being transformed to a new architecture**. Two contexts coexist — the as-is (legacy) and the to-be (target) — and the loop must bridge between them while keeping the system operational.

## Context landscape

**Context depth**: Dual — legacy and target coexist.

**Context shape**: Two contexts that must be bridged. The as-is (legacy architecture, old patterns, existing behaviour) and the to-be (target architecture, new patterns, desired behaviour).

## Phase adaptations

### Requirements phase

Gains a **discovery sub-phase**:
- Map the legacy system: architecture, dependencies, data flows, integration points
- Document business rules extracted from code analysis (not just stakeholder interviews)
- Define migration scope per iteration — what moves, what stays, what runs in parallel
- Establish non-functional requirements: performance baselines, uptime constraints, rollback criteria

### Implementation phase

Gains an **analyse-before-plan rhythm**:
- **Analyse legacy**: Profile the component being migrated — code structure, data access patterns, implicit business rules, performance characteristics
- **Design target**: Define the target component architecture, including how it coexists with remaining legacy during transition
- **Migrate**: Implement using incremental patterns (strangler fig, branch by abstraction, parallel run) that keep the system operational
- **Test both**: Validate that the migrated component works correctly AND that the overall system (legacy + new) remains stable under load
- **Document**: Update both as-is documentation (what was learned about legacy) and to-be documentation (how the new component works)

### Quality phase

Gains **dual-validation responsibility**:
- Does the migrated component meet its new architecture requirements?
- Does the overall system still function correctly with the change in place?
- Are performance characteristics maintained or improved?
- Can the change be safely rolled back if issues emerge in production?

## Common modernisation concerns

| Concern | Requirements impact | Implementation impact | Quality impact |
|---|---|---|---|
| Architecture decomposition | Define module boundaries and service contracts | Analyse coupling before extracting | Validate module integration |
| Business logic extraction | Document discovered rules as testable specs | Extract rules into explicit components | Verify rule preservation |
| Performance remediation | Define measurable performance targets | Profile before redesigning | Benchmark old vs new |
| Integration reliability | Document integration points and failure modes | Introduce resilience patterns | Validate under failure conditions |
| Observability | Define SLIs/SLOs, trace coverage, alert thresholds | Instrument both legacy and target | Verify observability standards met |
| Zero-downtime operation | Define rollback criteria, feature-flag strategy | Use strangler fig / canary patterns | Validate safe deployment |

## Context write emphasis

Very high — documenting both the understanding of legacy (as-is) and decisions about target (to-be). Migration patterns discovered in early cycles should be captured immediately via the Learning Loop to accelerate later cycles.

## Dual-context convergence

Over successive Delivery Loop cycles, the as-is context is gradually consumed and the to-be context grows — until the migration is complete and only the target context remains. Track this convergence explicitly to measure migration progress.

## When to use this profile

- Architecture modernisation (e.g. monolith to microservices)
- Cloud or infrastructure migration (as-is infrastructure + to-be infrastructure)
- Platform re-engineering while maintaining service continuity
- Systems running under production load that must remain operational during transformation
