# Code Quality Enhancement Plan

> **Parent**: [Modernisation Plan Overview](./README.md) | **Phases**: [Migration Phases](./05-migration-phases.md)
>
> **Skill Reference**: This phase is guided by the [performance-code-quality](../../.github/skills/performance-code-quality/SKILL.md) skill and its companion prompts.
>
> **Status**: ✅ Complete — delivered in 4 waves and merged via PRs [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148), [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150), [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151), and [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152).

## 1. Overview

Beyond adopting Java 21 language features, the OpenMRS Core codebase has structural code quality issues that should be addressed as part of the modernisation. This document captures findings from a systematic audit using the **performance-code-quality** skill and defines a new enhancement phase.

This work was delivered as **Phase 1.5** — after low-risk Java modernisation (Phase 1) and in parallel with Phase 2 (Containerisation). It focused on structural improvements that were independent of Java version but benefited from the modernisation momentum.

## 1.1 Completion Summary

Phase 1.5 is now fully complete on `master`.

- **Delivery model:** 4 focused waves, each merged independently to keep review scope small and verification clear.
- **Merged PRs:** [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148), [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150), [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151), and [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152)
- **Closed issues:** 11 Phase 1.5 implementation issues closed across those 4 PRs
- **Key delivery metrics:** ~20 focused classes extracted, 7 search-criteria records introduced, and ~2000+ lines net reduction across the decomposed high-complexity classes
- **CI enhancement:** reusable Maven cache composite action added at [`../../.github/actions/setup-maven/action.yml`](../../.github/actions/setup-maven/action.yml)
- **Review follow-through:** 10 Copilot review comments addressed on the final wave before merge

### Closed Issues

| Issue | Delivered in | Summary |
|---|---|---|
| [#93](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/93) | PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | Convert string switch dispatch to enum-based dispatch |
| [#96](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/96) | PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | Audit and resolve `@SuppressWarnings` annotations |
| [#99](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/99) | PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | Audit and remove dead `@Deprecated` code |
| [#102](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/102) | PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | Flatten deeply nested methods (>3 levels) in top 10 files |
| [#90](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/90) | PR [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150) | Audit and eliminate boolean flag parameters in public API |
| [#103](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/103) | PR [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150) | Refactor methods with 5+ parameters into config objects |
| [#67](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/67) | PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | Decompose `HibernateConceptDAO.java` |
| [#71](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/71) | PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | Decompose `ConceptServiceImpl.java` |
| [#78](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/78) | PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | Decompose `InitializationFilter.java` |
| [#82](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/82) | PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | Decompose `ModuleFactory.java` |
| [#87](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/87) | PR [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152) | Decompose remaining god classes (`Context`, `ORUR01Handler`, `OrderServiceImpl`, `PatientServiceImpl`, `ModuleUtil`) |

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

**Status**: ✅ Complete as part of the audit-led cleanup and complexity-reduction work merged during Waves 1-4.

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

**Status**: ✅ Complete across Waves 3 and 4 via PRs [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) and [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152).

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

**Status**: ✅ Complete in Wave 2 via PR [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150).

Address the 324 boolean flag parameters:

1. **Audit**: Identify methods where boolean flags create two distinct code paths
2. **Split**: Replace with two clearly-named methods (e.g., `retirePatient()` and `unretirePatient()` instead of `setPatientRetired(boolean)`)
3. **Enum substitution**: Where a boolean selects between modes, introduce an enum

**Approach**: Focus on public API methods first (these affect external consumers), then internal methods.

### Priority 4 — Complexity Reduction

**Skill reference**: [performance-code-quality § Big O awareness, Algorithmic hygiene](../../.github/skills/performance-code-quality/SKILL.md)
**Prompt**: `code.complexity-audit`

**Status**: ✅ Complete via Wave 1 and Wave 2 follow-up refactors in PRs [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) and [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150).

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

## 7. Delivered GitHub Issues

| Issue | Title | Wave | PR | Status |
|---|---|---|---|---|
| P1.5-05 / [#67](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/67) | Decompose `HibernateConceptDAO.java` (2,405 lines) | Wave 3 | [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | ✅ Complete |
| P1.5-06 / [#71](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/71) | Decompose `ConceptServiceImpl.java` (2,343 lines) | Wave 3 | [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | ✅ Complete |
| P1.5-07 / [#78](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/78) | Decompose `InitializationFilter.java` (1,985 lines) | Wave 3 | [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | ✅ Complete |
| P1.5-08 / [#82](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/82) | Decompose `ModuleFactory.java` (1,597 lines) | Wave 3 | [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) | ✅ Complete |
| P1.5-09 / [#87](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/87) | Decompose remaining god classes (`Context`, `ORUR01Handler`, `OrderServiceImpl`, `PatientServiceImpl`, `ModuleUtil`) | Wave 4 | [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152) | ✅ Complete |
| P1.5-10 / [#90](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/90) | Audit and eliminate boolean flag parameters in public API | Wave 2 | [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150) | ✅ Complete |
| P1.5-11 / [#93](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/93) | Convert string switch dispatch to enum-based dispatch | Wave 1 | [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | ✅ Complete |
| P1.5-12 / [#96](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/96) | Audit and resolve `@SuppressWarnings` annotations | Wave 1 | [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | ✅ Complete |
| P1.5-13 / [#99](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/99) | Audit and remove dead `@Deprecated` code | Wave 1 | [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | ✅ Complete |
| P1.5-14 / [#102](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/102) | Flatten deeply nested methods (>3 levels) in top 10 files | Wave 1 | [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) | ✅ Complete |
| P1.5-15 / [#103](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/103) | Refactor methods with 5+ parameters into config objects | Wave 2 | [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150) | ✅ Complete |

---

## Phase 1.5 Delivery Status

| Wave | Scope | Status | PR |
|---|---|---|---|
| Wave 1 — Audit & Cleanup | Initial code-quality audit, baseline metrics, suppressions cleanup | ✅ Completed | [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148) |
| Wave 2 — Parameter Refactoring | Long parameter list refactoring, config/options object introduction | ✅ Completed | [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150) |
| Wave 3 — God Class Decomposition | Decompose HibernateConceptDAO, ConceptServiceImpl, InitializationFilter, ModuleFactory | ✅ Completed | [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151) |
| Wave 4 — Remaining God Classes | Decompose Context, ORUR01Handler, OrderServiceImpl, PatientServiceImpl, ModuleUtil | ✅ Completed | [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152) |

---

## Tracked Issues

### Completed Phase 1.5 Issues

- [x] [#93](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/93) — Convert string switch dispatch to enum-based dispatch — PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148)
- [x] [#96](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/96) — Audit and resolve `@SuppressWarnings` annotations — PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148)
- [x] [#99](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/99) — Audit and remove dead `@Deprecated` code — PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148)
- [x] [#102](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/102) — Flatten deeply nested methods (>3 levels) in top 10 files — PR [#148](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/148)
- [x] [#90](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/90) — Audit and eliminate boolean flag parameters in public API — PR [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150)
- [x] [#103](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/103) — Refactor methods with 5+ parameters into config objects — PR [#150](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/150)
- [x] [#67](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/67) — Decompose `HibernateConceptDAO.java` — PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151)
- [x] [#71](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/71) — Decompose `ConceptServiceImpl.java` — PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151)
- [x] [#78](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/78) — Decompose `InitializationFilter.java` — PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151)
- [x] [#82](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/82) — Decompose `ModuleFactory.java` — PR [#151](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/151)
- [x] [#87](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/issues/87) — Decompose remaining god classes (`Context`, `ORUR01Handler`, `OrderServiceImpl`, `PatientServiceImpl`, `ModuleUtil`) — PR [#152](https://github.com/epam-acme-bookstore-demo-org/openmrs-core/pull/152)
