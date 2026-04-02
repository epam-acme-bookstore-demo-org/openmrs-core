# Code Quality Enhancement Plan

> **Parent**: [Modernisation Plan Overview](./README.md) | **Phases**: [Migration Phases](./05-migration-phases.md)
>
> **Skill Reference**: This phase is guided by the [performance-code-quality](../../.github/skills/performance-code-quality/SKILL.md) skill and its companion prompts.

## 1. Overview

Beyond adopting Java 21 language features, the OpenMRS Core codebase has structural code quality issues that should be addressed as part of the modernisation. This document captures findings from a systematic audit using the **performance-code-quality** skill and defines a new enhancement phase.

This work is planned as **Phase 1.5** — running after low-risk Java modernisation (Phase 1) and in parallel with Phase 2 (Containerisation). It focuses on structural improvements that are independent of Java version but benefit from the modernisation momentum.

## 2. Audit Findings

### 2.1 Codebase Scale

| Metric | Count |
|---|---|
| Production Java files (`api` + `web`) | 835 |
| Total production lines | ~158,000 |
| Test files | 359 |

### 2.2 God Files (>400 lines)

**92 files** exceed the recommended 400-line limit. The worst offenders:

| File | Lines | Responsibilities |
|---|---|---|
| `HibernateConceptDAO.java` | 2,405 | All concept-related database operations |
| `ConceptServiceImpl.java` | 2,343 | Concept service implementation with all concept operations |
| `ConceptService.java` | 2,202 | Concept service interface |
| `OpenmrsUtil.java` | 2,160 | General-purpose utility methods (classic "Utils" god class) |
| `InitializationFilter.java` | 1,985 | Web installation/setup wizard — all steps in one filter |
| `Concept.java` | 1,705 | Domain entity with extensive logic |
| `PatientServiceImpl.java` | 1,638 | Patient service implementation |
| `ModuleFactory.java` | 1,597 | Module loading, starting, stopping — all lifecycle in one class |
| `Context.java` | 1,515 | Application context and service locator |
| `OrderServiceImpl.java` | 1,399 | Order service implementation |
| `Obs.java` | 1,328 | Observation entity with complex logic |
| `ModuleUtil.java` | 1,311 | Module utility methods |
| `ORUR01Handler.java` | 1,272 | HL7 ORU^R01 message handler — all parsing in one class |
| `OpenmrsConstants.java` | 1,248 | Constants class (acceptable for constants) |
| `Person.java` | 1,239 | Person entity |
| `HL7ServiceImpl.java` | 1,202 | HL7 service implementation |
| `WebModuleUtil.java` | 1,026 | Web module utilities |

**Assessment**: The top 17 files alone account for ~28,000 lines (~18% of the codebase). These are classic "god classes" that violate the Single Responsibility Principle.

### 2.3 Broad Exception Handling

| Pattern | Count | Severity |
|---|---|---|
| `catch (Exception ...)` — broad catch | 256 | High |
| `catch (Throwable ...)` | 1 | Critical |
| Catch blocks with only comments (silent swallowing) | 71 | High |
| **Total problematic handlers** | **328** | |

**Assessment**: 256 broad exception handlers across 835 files means roughly 1 in 3 files has at least one overly broad catch. 71 silently swallowed exceptions are a reliability risk — errors are hidden rather than surfaced.

### 2.4 Long Parameter Lists

| Pattern | Count |
|---|---|
| Methods with 5+ parameters | 24 |
| Boolean flag parameters | 324 |

**Assessment**: 324 boolean parameters suggest many methods with branching behaviour controlled by flags. These should be split into separate methods or use enum/strategy patterns.

### 2.5 String-Typed Dispatch

| Pattern | Count |
|---|---|
| `case "..."` string switch clauses | 19 |

**Assessment**: Moderate. Each string switch should be evaluated for conversion to enum-based dispatch.

### 2.6 Deprecated and Suppressed Code

| Pattern | Count |
|---|---|
| `@Deprecated` annotations | 123 |
| `@SuppressWarnings` annotations | 116 |

**Assessment**: 123 deprecated APIs suggest accumulated technical debt that should be cleaned up. 116 suppressed warnings may hide legitimate issues.

## 3. Enhancement Categories

### Priority 1 — Error Handling Hardening

**Skill reference**: [performance-code-quality § Error handling discipline](../../.github/skills/performance-code-quality/SKILL.md)
**Prompt**: `code.harden-errors`

Address the 328 problematic exception handlers:

1. **Replace broad `catch (Exception e)` with specific types** — identify what exceptions each block is actually handling and narrow the catch
2. **Remove silent swallowing** — add logging with context to the 71 catch blocks that only have comments or empty bodies
3. **Establish consistent error strategy per layer**:
   - DAO layer: throw `DAOException` subtypes
   - Service layer: throw `APIException` subtypes
   - Web layer: catch and translate to HTTP responses
4. **Add context to error messages** — include what was attempted and which input failed

**Approach**: Module by module, file by file. Start with `api` module (largest exposure), then `web`.

### Priority 2 — God Class Decomposition

**Skill reference**: [performance-code-quality § File and function size](../../.github/skills/performance-code-quality/SKILL.md)
**Prompt**: `code.split-god-file`

Target the top 10 largest files for decomposition:

| File | Strategy |
|---|---|
| `OpenmrsUtil.java` (2,160 lines) | Split by domain: string utils, date utils, file utils, collection utils |
| `HibernateConceptDAO.java` (2,405 lines) | Split by operation type: CRUD, search, mapping, validation |
| `ConceptServiceImpl.java` (2,343 lines) | Split to delegate classes per sub-domain (concepts, concept sets, mappings) |
| `InitializationFilter.java` (1,985 lines) | Extract each wizard step into its own handler class |
| `ModuleFactory.java` (1,597 lines) | Split lifecycle phases: loading, starting, stopping, dependency resolution |
| `Context.java` (1,515 lines) | Extract service resolution, authentication, and thread context management |
| `ORUR01Handler.java` (1,272 lines) | Split by HL7 segment: PID handler, OBR handler, OBX handler |
| `ModuleUtil.java` (1,311 lines) | Split by concern: file operations, version comparison, URL handling |
| `OrderServiceImpl.java` (1,399 lines) | Split by order type processing |
| `PatientServiceImpl.java` (1,638 lines) | Delegate to sub-services for merge, search, identifier management |

**Approach**: Use the `code.split-god-file` prompt. Preserve public APIs via re-exports. One PR per file decomposition.

### Priority 3 — Boolean Flag Elimination

**Skill reference**: [performance-code-quality § Parameter and function signature hygiene](../../.github/skills/performance-code-quality/SKILL.md)

Address the 324 boolean flag parameters:

1. **Audit**: Identify methods where boolean flags create two distinct code paths
2. **Split**: Replace with two clearly-named methods (e.g., `retirePatient()` and `unretirePatient()` instead of `setPatientRetired(boolean)`)
3. **Enum substitution**: Where a boolean selects between modes, introduce an enum

**Approach**: Focus on public API methods first (these affect external consumers), then internal methods.

### Priority 4 — Complexity Reduction

**Skill reference**: [performance-code-quality § Big O awareness, Algorithmic hygiene](../../.github/skills/performance-code-quality/SKILL.md)
**Prompt**: `code.complexity-audit`

1. **Deep nesting**: Flatten methods with >3 levels of nesting using guard clauses and early returns
2. **Long parameter lists**: Group the 24 methods with 5+ parameters into config/options objects
3. **String dispatch**: Convert 19 string switch cases to enum-based dispatch where appropriate
4. **Deprecated API cleanup**: Audit 123 `@Deprecated` annotations — remove dead code, modernise replacements
5. **SuppressWarnings audit**: Review 116 `@SuppressWarnings` — resolve the underlying warnings where possible

## 4. Module-by-Module Approach

| Order | Module | Focus | Estimated Effort |
|---|---|---|---|
| 1 | `api` | Error handling, god classes (top priority — most files) | Large |
| 2 | `web` | Error handling, InitializationFilter decomposition, WebModuleUtil | Medium |
| 3 | `test-suite` | Ensure refactored code passes integration tests | Small |

## 5. Validation

For every code quality PR:

1. All existing tests must pass: `mvn verify`
2. SpotBugs findings must not increase: `mvn spotbugs:check`
3. Checkstyle must pass: `mvn checkstyle:check`
4. Spotless must pass: `mvn spotless:check`
5. JaCoCo coverage must not decrease
6. No new `@SuppressWarnings` added without justification

## 6. Tooling

The following prompts from the `performance-code-quality` skill should be used during this phase:

| Prompt | Use Case |
|---|---|
| `code.complexity-audit` | Initial audit of each module before changes |
| `code.review-quality` | Review each PR against the full quality checklist |
| `code.split-god-file` | Guided decomposition of oversized files |
| `code.harden-errors` | Systematic error handling improvements |
| `code.refactor` | General refactoring guidance |

## 7. GitHub Issues

| ID | Title | Type | Labels | Priority |
|---|---|---|---|---|
| P1.5-01 | Error handling hardening — `api` module (256 broad catches) | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | P1 |
| P1.5-02 | Error handling hardening — `web` module | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | P1 |
| P1.5-03 | Remove silent exception swallowing (71 catch blocks) | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | P1 |
| P1.5-04 | Decompose `OpenmrsUtil.java` (2,160 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-05 | Decompose `HibernateConceptDAO.java` (2,405 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-06 | Decompose `ConceptServiceImpl.java` (2,343 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-07 | Decompose `InitializationFilter.java` (1,985 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-08 | Decompose `ModuleFactory.java` (1,597 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-09 | Decompose remaining god classes (Context, ORUR01Handler, OrderServiceImpl, PatientServiceImpl, ModuleUtil) | Epic | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | P2 |
| P1.5-10 | Audit and eliminate boolean flag parameters in public API | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |
| P1.5-11 | Convert string switch dispatch to enum-based dispatch | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |
| P1.5-12 | Audit and resolve `@SuppressWarnings` annotations | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |
| P1.5-13 | Audit and remove dead `@Deprecated` code | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |
| P1.5-14 | Flatten deeply nested methods (>3 levels) in top 10 files | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |
| P1.5-15 | Refactor methods with 5+ parameters into config objects | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | P3 |

---

## Tracked Issues

### Priority 1 — Error Handling Hardening

- [ ] #15 — Error handling hardening — api module (256 broad catches)
- [ ] #30 — Error handling hardening — web module
- [ ] #44 — Remove silent exception swallowing (71 catch blocks)

### Priority 2 — God Class Decomposition

- [ ] #48 — Decompose OpenmrsUtil.java (2,160 lines)
- [ ] #67 — Decompose HibernateConceptDAO.java (2,405 lines)
- [ ] #71 — Decompose ConceptServiceImpl.java (2,343 lines)
- [ ] #78 — Decompose InitializationFilter.java (1,985 lines)
- [ ] #82 — Decompose ModuleFactory.java (1,597 lines)
- [ ] #87 — Decompose remaining god classes (Context, ORUR01Handler, OrderServiceImpl, PatientServiceImpl, ModuleUtil)

### Priority 3 — Complexity Reduction

- [ ] #90 — Audit and eliminate boolean flag parameters in public API
- [ ] #93 — Convert string switch dispatch to enum-based dispatch
- [ ] #96 — Audit and resolve @SuppressWarnings annotations
- [ ] #99 — Audit and remove dead @Deprecated code
- [ ] #102 — Flatten deeply nested methods (>3 levels) in top 10 files
- [ ] #103 — Refactor methods with 5+ parameters into config objects
