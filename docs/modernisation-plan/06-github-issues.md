# GitHub Issues Structure

> **Parent**: [Modernisation Plan Overview](./README.md) | **Phases**: [Migration Phases](./05-migration-phases.md)

## 1. Organisation

### Hierarchy

```
Epic (1 per phase)
└── Story / Task (granular work item)
    └── Sub-task (optional, for large stories)
```

- **Epics** group all work for a phase. Each epic links to its section in [05-migration-phases.md](./05-migration-phases.md).
- **Stories** describe a user-facing or developer-facing outcome.
- **Tasks** describe technical work that delivers no direct user-facing change.
- Every issue links back to the plan document and section it implements.

### Labels

| Label | Color (hex) | Scope |
|---|---|---|
| `modernisation` | `#0052CC` | All modernisation work |
| `java-21` | `#0E8A16` | Java language modernisation |
| `azure` | `#008AD7` | Azure infrastructure |
| `containers` | `#7B61FF` | Docker / container image work |
| `testing` | `#FBCA04` | Test infrastructure and automation |
| `documentation` | `#F9A825` | Documentation updates |
| `phase-0` | `#B0BEC5` | Foundation |
| `phase-1` | `#B0BEC5` | Low-risk Java |
| `phase-2` | `#B0BEC5` | Containers & dev environment |
| `phase-3` | `#B0BEC5` | Medium-risk Java |
| `phase-4` | `#B0BEC5` | Production infrastructure |
| `phase-5` | `#B0BEC5` | High-risk changes & cleanup |
| `phase-6` | `#B0BEC5` | Go-live |
| `priority-1` | `#D93F0B` | High priority |
| `phase-1.5` | `#B0BEC5` | Code quality enhancement |
| `priority-2` | `#E99695` | Medium priority |
| `priority-3` | `#FEF2C0` | Low priority |
| `breaking-change` | `#B60205` | Changes public API |
| `needs-decision` | `#D876E3` | Requires stakeholder decision |

### Plan Reference Format

Every issue description must include a plan reference linking to the source section:

```
📋 **Plan Reference**: [01-java-language-modernisation.md § 3.1 — Pattern Matching instanceof](./docs/modernisation-plan/01-java-language-modernisation.md)
```

### Issue Template

```markdown
## Context

📋 **Plan Reference**: [<document>#<section>](./docs/modernisation-plan/<document>)
**Epic**: #<epic-issue-number>
**Phase**: <phase number and name>

## Objective

<What this issue achieves and why it matters for the modernisation>

## Scope

<Files, modules, or infrastructure components in scope>

## Acceptance Criteria

- [ ] <Criterion 1>
- [ ] <Criterion 2>
- [ ] <Criterion 3>

## Validation

<Commands or checks to verify completion>

## Dependencies

- **Depends on**: #<issue> — <reason>
- **Blocks**: #<issue> — <reason>

## Notes

<Any additional context, risks, or decisions needed>
```

---

## 2. Epics

| # | Epic | Phase | Labels |
|---|---|---|---|
| E0 | Foundation for OpenMRS Core Modernisation | 0 | `modernisation`, `phase-0` |
| E1 | Low-Risk Java 21 Modernisation | 1 | `modernisation`, `java-21`, `phase-1` |
| E1.5 | Code Quality Enhancement | 1.5 | `modernisation`, `java-21`, `phase-1.5` |
| E2 | Containerisation and Dev Azure Environment | 2 | `modernisation`, `containers`, `azure`, `phase-2` |
| E3 | Medium-Risk Java 21 Modernisation | 3 | `modernisation`, `java-21`, `phase-3` |
| E4 | Production Azure Infrastructure | 4 | `modernisation`, `azure`, `phase-4` |
| E5 | High-Risk Java Changes and Cleanup | 5 | `modernisation`, `java-21`, `phase-5` |
| E6 | Production Migration and Go-Live | 6 | `modernisation`, `phase-6` |

---

## 3. Issues by Phase

### Phase 0 — Foundation

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P0-01 | Set up GitHub Actions CI pipeline for modernisation branches | Task | `modernisation`, `testing`, `phase-0`, `priority-1` | — | [04-testing-strategy.md § CI Pipeline](./04-testing-strategy.md) |
| P0-02 | Document Java 21 coding standards for OpenMRS Core | Task | `modernisation`, `java-21`, `documentation`, `phase-0`, `priority-1` | — | [01-java-language-modernisation.md § Migration Rules](./01-java-language-modernisation.md) |
| P0-03 | Update PR template and review checklist for modernisation work | Task | `modernisation`, `documentation`, `phase-0`, `priority-1` | P0-02 | [01-java-language-modernisation.md § Coding Standards](./01-java-language-modernisation.md) |
| P0-04 | Capture baseline test coverage, SpotBugs, startup, and latency metrics | Task | `modernisation`, `testing`, `phase-0`, `priority-1` | P0-01 | [04-testing-strategy.md § Performance Baseline](./04-testing-strategy.md) |
| P0-05 | Provision Azure subscription access and resource groups for dev and prod | Task | `modernisation`, `azure`, `phase-0`, `priority-1` | — | [03-azure-infrastructure.md § Architecture Overview](./03-azure-infrastructure.md) |
| P0-06 | Evaluate OpenRewrite for automated Java 21 migration | Task | `modernisation`, `java-21`, `phase-0`, `priority-2` | — | [01-java-language-modernisation.md § Tooling Support](./01-java-language-modernisation.md) |
| P0-07 | Update Spotless and Checkstyle configuration for Java 21 syntax | Task | `modernisation`, `java-21`, `phase-0`, `priority-1` | P0-02 | [01-java-language-modernisation.md § Tooling Support](./01-java-language-modernisation.md) |
| P0-08 | Establish test coverage baseline with JaCoCo | Task | `modernisation`, `testing`, `phase-0`, `priority-1` | P0-01 | [04-testing-strategy.md § Quality Gates](./04-testing-strategy.md) |
| P0-09 | Create Lucidchart architecture diagrams for Azure dev and production environments | Task | `modernisation`, `azure`, `documentation`, `phase-0`, `priority-1` | P0-05 | [03-azure-infrastructure.md](./03-azure-infrastructure.md) |
| P0-10 | Create Lucidchart phase dependency and CI/CD pipeline diagrams | Task | `modernisation`, `documentation`, `phase-0`, `priority-2` | — | [05-migration-phases.md](./05-migration-phases.md) |

---

### Phase 1 — Low-Risk Java 21 Modernisation

All Phase 1 issues depend on **Epic E0** being complete.

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P1-01 | Apply pattern matching `instanceof` — `tools` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-02 | Apply pattern matching `instanceof` — `test` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-01 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-03 | Apply pattern matching `instanceof` — `liquibase` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-02 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-04 | Apply pattern matching `instanceof` — `api` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-03 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-05 | Apply pattern matching `instanceof` — `web` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-04 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-06 | Apply pattern matching `instanceof` — `webapp` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-05 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-07 | Apply pattern matching `instanceof` — `test-suite` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-06 | [01-java §3.1](./01-java-language-modernisation.md) |
| P1-08 | Adopt `var` for local variables — `tools` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-09 | Adopt `var` for local variables — `test` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-08 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-10 | Adopt `var` for local variables — `liquibase` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-09 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-11 | Adopt `var` for local variables — `api` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-10 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-12 | Adopt `var` for local variables — `web` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-11 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-13 | Adopt `var` for local variables — `webapp` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-12 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-14 | Adopt `var` for local variables — `test-suite` module | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-13 | [01-java §3.2](./01-java-language-modernisation.md) |
| P1-15 | Convert switch statements to switch expressions — all modules | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.3](./01-java-language-modernisation.md) |
| P1-16 | Adopt text blocks for multi-line strings — all modules | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.4](./01-java-language-modernisation.md) |
| P1-17 | Adopt String API improvements (`isBlank`, `strip`, etc.) — all modules | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.5](./01-java-language-modernisation.md) |
| P1-18 | Adopt Collections factory methods (`List.of`, `Map.of`) — all modules | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | E0 | [01-java §3.6](./01-java-language-modernisation.md) |
| P1-19 | Simplify try-with-resources patterns — all modules | Task | `modernisation`, `java-21`, `phase-1`, `priority-2` | E0 | [01-java §3.7](./01-java-language-modernisation.md) |
| P1-20 | Phase 1 rollout tracking and merge readiness review | Task | `modernisation`, `java-21`, `phase-1`, `priority-1` | P1-01..P1-19 | [05-migration-phases.md § Phase 1](./05-migration-phases.md) |

---

### Phase 2 — Containerisation & Dev Azure Environment

Phase 2 runs **in parallel** with Phase 1. Depends on **Epic E0**.

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P2-01 | Switch production Docker image to JRE-only base | Task | `modernisation`, `containers`, `phase-2`, `priority-1` | E0 | [02-containerisation.md § Image Optimisation](./02-containerisation.md) |
| P2-02 | Add JVM container tuning flags (MaxRAMPercentage, G1GC) | Task | `modernisation`, `containers`, `phase-2`, `priority-1` | P2-01 | [02-containerisation.md § JVM Tuning](./02-containerisation.md) |
| P2-03 | Optimise Dockerfile layer caching and build performance | Task | `modernisation`, `containers`, `phase-2`, `priority-2` | P2-01 | [02-containerisation.md § Layer Optimisation](./02-containerisation.md) |
| P2-04 | Add OCI standard labels and image metadata | Task | `modernisation`, `containers`, `phase-2`, `priority-3` | P2-01 | [02-containerisation.md § Security](./02-containerisation.md) |
| P2-05 | Review and update `.dockerignore` | Task | `modernisation`, `containers`, `phase-2`, `priority-3` | — | [02-containerisation.md § .dockerignore](./02-containerisation.md) |
| P2-06 | Deploy Azure Container Registry (Basic SKU) | Task | `modernisation`, `azure`, `containers`, `phase-2`, `priority-1` | P0-05 | [03-azure-infrastructure.md § Dev Environment](./03-azure-infrastructure.md) |
| P2-07 | Configure container image vulnerability scanning | Task | `modernisation`, `containers`, `phase-2`, `priority-1` | P2-06 | [02-containerisation.md § Security](./02-containerisation.md) |
| P2-08 | Create GitHub Actions container build workflow (build → test → scan → push) | Task | `modernisation`, `containers`, `testing`, `phase-2`, `priority-1` | P2-06, P2-07 | [02-containerisation.md § CI/CD Pipeline](./02-containerisation.md) |
| P2-09 | Set up multi-platform builds with Docker Buildx (amd64 + arm64) | Task | `modernisation`, `containers`, `phase-2`, `priority-2` | P2-08 | [02-containerisation.md § Multi-Platform](./02-containerisation.md) |
| P2-10 | Deploy dev Container Apps Environment (Consumption plan) + PostgreSQL Flexible Server | Task | `modernisation`, `azure`, `phase-2`, `priority-1` | P2-06 | [03-azure-infrastructure.md § Dev Environment](./03-azure-infrastructure.md) |
| P2-11 | Deploy Azure Key Vault (dev) and seed secrets | Task | `modernisation`, `azure`, `phase-2`, `priority-1` | P0-05 | [03-azure-infrastructure.md § Dev Environment](./03-azure-infrastructure.md) |
| P2-12 | Deploy OpenMRS Core Container App to dev environment | Task | `modernisation`, `azure`, `containers`, `phase-2`, `priority-1` | P2-08, P2-10, P2-11 | [03-azure-infrastructure.md § Dev Environment](./03-azure-infrastructure.md) |
| P2-13 | Create automated smoke test suite for post-deployment validation | Task | `modernisation`, `testing`, `phase-2`, `priority-1` | P2-12 | [04-testing-strategy.md § Smoke Tests](./04-testing-strategy.md) |
| P2-14 | Validate dev deployment end-to-end (health, DB, Liquibase, login) | Task | `modernisation`, `testing`, `azure`, `phase-2`, `priority-1` | P2-13 | [04-testing-strategy.md § Infrastructure Testing](./04-testing-strategy.md) |
| P2-15 | Create `.env.example` for docker-compose secrets management | Task | `modernisation`, `containers`, `phase-2`, `priority-2` | — | [02-containerisation.md § Docker Compose](./02-containerisation.md) |
| P2-16 | Create `docker-compose.test.yml` for CI integration testing | Task | `modernisation`, `containers`, `testing`, `phase-2`, `priority-2` | — | [02-containerisation.md § Docker Compose](./02-containerisation.md) |
| P2-17 | Document image tagging and promotion strategy | Task | `modernisation`, `documentation`, `containers`, `phase-2`, `priority-2` | P2-08 | [02-containerisation.md § Tagging Strategy](./02-containerisation.md) |
| P2-18 | Add VS Code Dev Container configuration | Task | `modernisation`, `containers`, `phase-2`, `priority-3` | — | [02-containerisation.md § Dev Dockerfile](./02-containerisation.md) |

---

### Phase 3 — Medium-Risk Java 21 Modernisation

Depends on **Epic E1** (Phase 1 complete). Runs **in parallel** with Phase 4.

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P3-01 | Identify DTO and value object candidates for record conversion | Story | `modernisation`, `java-21`, `phase-3`, `priority-1` | E1 | [01-java §3.8](./01-java-language-modernisation.md) |
| P3-02 | Convert low-risk DTOs to records in internal modules | Task | `modernisation`, `java-21`, `phase-3`, `priority-1` | P3-01 | [01-java §3.8](./01-java-language-modernisation.md) |
| P3-03 | Convert DTOs to records in `api` module | Task | `modernisation`, `java-21`, `phase-3`, `priority-1`, `breaking-change` | P3-02 | [01-java §3.8](./01-java-language-modernisation.md) |
| P3-04 | Identify closed type hierarchies suitable for sealed classes | Story | `modernisation`, `java-21`, `phase-3`, `priority-2` | E1 | [01-java §3.9](./01-java-language-modernisation.md) |
| P3-05 | Apply sealed classes to approved type hierarchies | Task | `modernisation`, `java-21`, `phase-3`, `priority-2` | P3-04 | [01-java §3.9](./01-java-language-modernisation.md) |
| P3-06 | `java.time` migration — Phase A: Internal/private methods | Task | `modernisation`, `java-21`, `phase-3`, `priority-1` | E1 | [01-java §3.10](./01-java-language-modernisation.md) |
| P3-07 | `java.time` migration — Phase B: Add `java.time` overloads to public API (deprecate `Date` versions) | Story | `modernisation`, `java-21`, `phase-3`, `priority-1`, `breaking-change` | P3-06 | [01-java §3.10](./01-java-language-modernisation.md) |
| P3-08 | `java.time` migration — Phase C: Migrate callers module by module | Task | `modernisation`, `java-21`, `phase-3`, `priority-1` | P3-07 | [01-java §3.10](./01-java-language-modernisation.md) |
| P3-09 | `java.time` migration — Phase D: Remove deprecated `Date`-based methods | Story | `modernisation`, `java-21`, `phase-3`, `priority-2`, `breaking-change`, `needs-decision` | P3-08 | [01-java §3.10](./01-java-language-modernisation.md) |
| P3-10 | Modernise Stream API usage (`.toList()`, `mapMulti()`) — all modules | Task | `modernisation`, `java-21`, `phase-3`, `priority-2` | E1 | [01-java §3.11](./01-java-language-modernisation.md) |
| P3-11 | Add serialization round-trip tests for `java.time` types | Task | `modernisation`, `testing`, `java-21`, `phase-3`, `priority-1` | P3-07 | [04-testing-strategy.md § java.time Testing](./04-testing-strategy.md) |
| P3-12 | Prepare public API `java.time` migration proposal and versioning strategy | Story | `modernisation`, `java-21`, `documentation`, `phase-3`, `priority-1`, `needs-decision` | P3-06 | [01-java §3.10](./01-java-language-modernisation.md) |
| P3-13 | Create and maintain Phase 3 `java.time` migration tracker | Task | `modernisation`, `java-21`, `phase-3`, `priority-2` | P3-06 | [05-migration-phases.md § Phase 3](./05-migration-phases.md) |

---

### Phase 4 — Production Azure Infrastructure

Depends on **Epic E2** (Phase 2 complete — dev environment proven). Runs **in parallel** with Phase 3.

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P4-01 | Provision production VNET (10.0.0.0/16) and subnet layout | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | E2 | [03-azure §3.2](./03-azure-infrastructure.md) |
| P4-02 | Configure NSGs for all production subnets | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-01 | [03-azure §3.2](./03-azure-infrastructure.md) |
| P4-03 | Deploy production PostgreSQL Flexible Server with private access and HA | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-01 | [03-azure §3.5](./03-azure-infrastructure.md) |
| P4-04 | Deploy production Key Vault with private endpoint | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-01 | [03-azure §3.6](./03-azure-infrastructure.md) |
| P4-05 | Upgrade ACR to Standard SKU with private endpoint | Task | `modernisation`, `azure`, `containers`, `phase-4`, `priority-1` | P4-01 | [03-azure §3.6](./03-azure-infrastructure.md) |
| P4-06 | Deploy Container Apps Environment into VNET (Workload profiles) | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-01, P4-02 | [03-azure §3.3](./03-azure-infrastructure.md) |
| P4-07 | Deploy OpenMRS Container App with internal-only ingress | Task | `modernisation`, `azure`, `containers`, `phase-4`, `priority-1` | P4-03, P4-04, P4-05, P4-06 | [03-azure §3.3](./03-azure-infrastructure.md) |
| P4-08 | Deploy Application Gateway WAF_v2 | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-07 | [03-azure §3.4](./03-azure-infrastructure.md) |
| P4-09 | Configure TLS termination and custom domain on Application Gateway | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-08 | [03-azure §3.4](./03-azure-infrastructure.md) |
| P4-10 | Configure managed identities and RBAC for Container App → Key Vault / ACR | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-07 | [03-azure §3.6](./03-azure-infrastructure.md) |
| P4-11 | Set up monitoring, alerting, and dashboards (App Insights + Log Analytics) | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-07 | [03-azure §3.7](./03-azure-infrastructure.md) |
| P4-12 | Enable Microsoft Defender for Containers and Database | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-07, P4-03 | [03-azure §3.6](./03-azure-infrastructure.md) |
| P4-13 | Run production security hardening review and remediation | Story | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-08, P4-09, P4-10, P4-11, P4-12 | [03-azure §3.6](./03-azure-infrastructure.md) |
| P4-14 | Create Bicep IaC modules for all production resources | Task | `modernisation`, `azure`, `phase-4`, `priority-1` | P4-01..P4-12 | [03-azure § IaC](./03-azure-infrastructure.md) |
| P4-15 | Create infrastructure deployment GitHub Actions workflow (OIDC auth) | Task | `modernisation`, `azure`, `testing`, `phase-4`, `priority-1` | P4-14 | [03-azure § CI/CD](./03-azure-infrastructure.md) |
| P4-16 | Create application deployment GitHub Actions workflow | Task | `modernisation`, `azure`, `containers`, `phase-4`, `priority-1` | P4-15 | [03-azure § CI/CD](./03-azure-infrastructure.md) |

---

### Phase 5 — High-Risk Java Changes & Cleanup

Depends on **Epic E3** (Phase 3 complete).

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P5-01 | Investigate `OpenmrsSecurityManager` usage, callers, and module system impact | Story | `modernisation`, `java-21`, `phase-5`, `priority-1` | E3 | [01-java §3.12](./01-java-language-modernisation.md) |
| P5-02 | Design `SecurityManager` replacement model (propose `StackWalker` alternative) | Story | `modernisation`, `java-21`, `phase-5`, `priority-1`, `needs-decision` | P5-01 | [01-java §3.12](./01-java-language-modernisation.md) |
| P5-03 | Implement `SecurityManager` removal and approved replacement | Task | `modernisation`, `java-21`, `phase-5`, `priority-1`, `breaking-change` | P5-02 | [01-java §3.12](./01-java-language-modernisation.md) |
| P5-04 | Assess and migrate legacy HTTP client usage to `java.net.http.HttpClient` | Task | `modernisation`, `java-21`, `phase-5`, `priority-2` | E3 | [01-java §3.13](./01-java-language-modernisation.md) |
| P5-05 | Apply Optional API improvements (`ifPresentOrElse`, `or`, `stream`) | Task | `modernisation`, `java-21`, `phase-5`, `priority-3` | E3 | [01-java §3.14](./01-java-language-modernisation.md) |
| P5-06 | Remove transitional deprecated APIs introduced during modernisation | Task | `modernisation`, `java-21`, `phase-5`, `priority-2` | P5-03, P5-04 | [05-migration-phases.md § Phase 5](./05-migration-phases.md) |
| P5-07 | Performance benchmarking — compare against Phase 0 baseline | Task | `modernisation`, `testing`, `phase-5`, `priority-1` | P5-03, P5-04, P5-05, P5-06 | [04-testing-strategy.md § Performance](./04-testing-strategy.md) |

---

### Phase 6 — Production Migration & Go-Live

Depends on **Epics E4 and E5** (Phases 4 and 5 complete).

| ID | Title | Type | Labels | Depends On | Plan Reference |
|---|---|---|---|---|---|
| P6-01 | Pre-production validation — full regression suite in staging | Task | `modernisation`, `testing`, `phase-6`, `priority-1` | E4, E5 | [04-testing-strategy.md § Smoke Tests](./04-testing-strategy.md) |
| P6-02 | Create DNS cutover and rollback plan for Azure go-live | Story | `modernisation`, `azure`, `documentation`, `phase-6`, `priority-1` | P6-01 | [03-azure § Migration Path](./03-azure-infrastructure.md) |
| P6-03 | Assess and execute application data migration requirements | Task | `modernisation`, `azure`, `phase-6`, `priority-1`, `needs-decision` | P6-01 | [05-migration-phases.md § Phase 6](./05-migration-phases.md) |
| P6-04 | Load testing in production environment | Task | `modernisation`, `testing`, `azure`, `phase-6`, `priority-1` | P6-01 | [04-testing-strategy.md § Performance](./04-testing-strategy.md) |
| P6-05 | Validate blue-green deployment process for production cutover | Task | `modernisation`, `azure`, `phase-6`, `priority-1` | P6-01 | [05-migration-phases.md § Phase 6](./05-migration-phases.md) |
| P6-06 | Execute DNS cutover and go-live | Task | `modernisation`, `azure`, `phase-6`, `priority-1` | P6-02, P6-04, P6-05 | [03-azure § Migration Path](./03-azure-infrastructure.md) |
| P6-07 | Post-go-live monitoring — 48-hour intensive monitoring period | Task | `modernisation`, `azure`, `phase-6`, `priority-1` | P6-06 | [03-azure § Monitoring](./03-azure-infrastructure.md) |
| P6-08 | Write and review deployment, rollback, and incident runbooks | Task | `modernisation`, `documentation`, `phase-6`, `priority-1` | P6-05 | [05-migration-phases.md § Phase 6](./05-migration-phases.md) |
| P6-09 | Retrospective and lessons learned | Task | `modernisation`, `documentation`, `phase-6`, `priority-2` | P6-07 | [05-migration-phases.md § Phase 6](./05-migration-phases.md) |

---


### Phase 1.5 — Code Quality Enhancement

**Epic E1.5**: Code quality enhancements guided by `performance-code-quality` skill

📋 **Plan Reference**: [07-code-quality-enhancement.md](./07-code-quality-enhancement.md)

| ID | Title | Type | Labels | Depends On | Plan Section |
|---|---|---|---|---|---|
| P1.5-01 | Error handling hardening — `api` module (256 broad catches) | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | E1 | [07 § Priority 1](./07-code-quality-enhancement.md) |
| P1.5-02 | Error handling hardening — `web` module | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | E1 | [07 § Priority 1](./07-code-quality-enhancement.md) |
| P1.5-03 | Remove silent exception swallowing (71 catch blocks) | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-1` | P1.5-01 | [07 § Priority 1](./07-code-quality-enhancement.md) |
| P1.5-04 | Decompose `OpenmrsUtil.java` (2,160 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-05 | Decompose `HibernateConceptDAO.java` (2,405 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-06 | Decompose `ConceptServiceImpl.java` (2,343 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-07 | Decompose `InitializationFilter.java` (1,985 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-08 | Decompose `ModuleFactory.java` (1,597 lines) | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-09 | Decompose remaining god classes (Context, ORUR01Handler, OrderServiceImpl, PatientServiceImpl, ModuleUtil) | Epic | `modernisation`, `java-21`, `phase-1.5`, `priority-2` | E1 | [07 § Priority 2](./07-code-quality-enhancement.md) |
| P1.5-10 | Audit and eliminate boolean flag parameters in public API | Story | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |
| P1.5-11 | Convert string switch dispatch to enum-based dispatch | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |
| P1.5-12 | Audit and resolve `@SuppressWarnings` annotations | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |
| P1.5-13 | Audit and remove dead `@Deprecated` code | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |
| P1.5-14 | Flatten deeply nested methods (>3 levels) in top 10 files | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |
| P1.5-15 | Refactor methods with 5+ parameters into config objects | Task | `modernisation`, `java-21`, `phase-1.5`, `priority-3` | E1 | [07 § Priority 3](./07-code-quality-enhancement.md) |

---

## 4. Dependency Graph

```
                    ┌──────────────────────────────────────────────┐
                    │              PHASE 0: FOUNDATION             │
                    │  P0-01..P0-10                                │
                    └─────────────────┬────────────────────────────┘
                                      │
                    ┌─────────────────┼────────────────────┐
                    ▼                                      ▼
    ┌───────────────────────────┐          ┌───────────────────────────┐
    │   PHASE 1: LOW-RISK JAVA │          │  PHASE 2: CONTAINERS &   │
    │   P1-01..P1-20            │          │  DEV ENVIRONMENT          │
    │                           │          │  P2-01..P2-18             │
    └─────────────┬─────────────┘          └────────────┬──────────────┘
                  │                                     │
                  ▼                                     ▼
    ┌───────────────────────────┐          ┌───────────────────────────┐
    │  PHASE 3: MEDIUM-RISK    │          │  PHASE 4: PRODUCTION     │
    │  JAVA                     │          │  INFRASTRUCTURE           │
    │  P3-01..P3-13             │  parallel │  P4-01..P4-16             │
    └─────────────┬─────────────┘          └────────────┬──────────────┘
                  │                                     │
                  ▼                                     │
    ┌───────────────────────────┐                       │
    │  PHASE 5: HIGH-RISK &    │                       │
    │  CLEANUP                  │                       │
    │  P5-01..P5-07             │                       │
    └─────────────┬─────────────┘                       │
                  │                                     │
                  └─────────────────┬───────────────────┘
                                    ▼
                    ┌───────────────────────────┐
                    │  PHASE 6: GO-LIVE         │
                    │  P6-01..P6-09             │
                    └───────────────────────────┘
```

## 5. Summary

| Phase | Issues | Critical Path |
|---|---|---|
| Phase 0 | 10 | Yes — blocks all other phases |
| Phase 1 | 20 | Track A critical path |
| Phase 1.5 | 15 | Parallel with Track B |
| Phase 2 | 18 | Track B critical path |
| Phase 3 | 13 | Track A critical path |
| Phase 4 | 16 | Track B critical path |
| Phase 5 | 7 | Track A critical path |
| Phase 6 | 9 | Final convergence |
| **Total** | **108** | |

### Decision Points

The following issues are tagged `needs-decision` and require stakeholder input before work can proceed:

| Issue | Decision Required |
|---|---|
| P3-09 | Remove deprecated `Date`-based public API methods — accept breaking change or defer? |
| P3-12 | Public API `java.time` migration versioning strategy — minor or major version bump? |
| P5-02 | SecurityManager replacement model — which alternative security controls? |
| P6-03 | Data migration requirements — is schema migration needed or application-level only? |
