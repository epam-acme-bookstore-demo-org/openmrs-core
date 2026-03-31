# OpenMRS Core Modernisation Plan

## Executive summary

OpenMRS Core already builds on Java 21 and has completed major platform upgrades such as the move to Jakarta packages, Spring Framework 7, Hibernate 7, and Tomcat 11. However, the codebase still largely reflects Java 8-era coding patterns, and the current container setup is primarily aimed at local development rather than cloud deployment.

This modernisation plan has two primary goals:

1. Modernise OpenMRS Core code patterns from Java 8 style to Java 21 style, using newer language and library features where they improve clarity, safety, maintainability, and long-term supportability.
2. Define a container and Azure deployment approach that supports both:
   - a simple developer environment, and
   - a production deployment on Azure Container Apps with private networking and Application Gateway as the controlled ingress path.

This overview document is the entry point for the plan. Detailed decisions, sequencing, and implementation guidance are split across companion documents linked below. Individual delivery tasks will be tracked in GitHub Issues, and each issue should link back to the relevant plan document section.

## Current state assessment

### Snapshot

| Area | Current state | Planning implication |
| --- | --- | --- |
| Product | OpenMRS Core `3.0.0-SNAPSHOT` | Plan should target active trunk development, not a legacy branch |
| Java baseline | Maven compiler release is set to Java 21 | The runtime baseline is already in place; effort is primarily code modernisation, compatibility validation, and standards adoption |
| Code style | Production code still predominantly uses Java 8 idioms | Modernisation should focus on safe, incremental refactoring rather than a single disruptive rewrite |
| Web/runtime | Production Docker image runs on Tomcat 11 | Container and Azure work should optimise for Tomcat-based deployment, not Jetty |
| Frameworks | Spring Framework 7.0.6, Hibernate 7.2.6.Final, Jakarta namespace already adopted | The codebase is already on a modern platform stack, reducing framework upgrade pressure during this plan |
| Testing | JUnit 6.0.3, Mockito 5.23.0, Testcontainers 2.0.4 | The test stack is modern enough to support refactoring and container validation work |
| Database | MariaDB is the primary local/container database; MySQL and PostgreSQL are supported in tests | Database compatibility must remain explicit throughout container and migration work |
| Build/CI | Bamboo Specs are present under `bamboo-specs/` | CI changes must fit Bamboo-based pipelines unless a separate CI migration is approved |
| Packaging | Multi-stage Dockerfile exists, but current workflow is still local-dev oriented | Container work should focus on producing cloud-ready images and deployment conventions |
| Repository shape | Root Maven build includes 8 modules: `bom`, `tools`, `test`, `api`, `web`, `webapp`, `liquibase`, `test-suite` | Work should be sequenced by module ownership and blast radius |

### Evidence of the Java modernisation gap

The platform baseline is current, but language adoption is not. Examples from the current codebase include:

| Indicator | Current observation | Why it matters |
| --- | --- | --- |
| `var` adoption | Only limited use across the repository | Suggests most code still follows pre-Java 10 style |
| Records | No record declarations in production code | Opportunities remain for clearer immutable DTO-style types where appropriate |
| Sealed classes | No usage found | Indicates newer domain modelling techniques have not yet been adopted |
| Text blocks | No usage found | Multi-line SQL, JSON, XML, and message templates remain more verbose than necessary |
| Switch expressions | No usage found | Control-flow remains on older syntax even where newer forms would improve readability |
| Pattern matching for `instanceof` | No usage found | Existing code continues to use verbose `instanceof` plus explicit casts |
| Date/time APIs | Hundreds of files still import `java.util.Date` | Migration to `java.time` remains a significant and potentially cross-cutting task |
| SecurityManager dependency | `OpenmrsSecurityManager` still exists | This is a compatibility risk because `SecurityManager` has been deprecated for years and removed for application use in newer Java releases |
| Raw types | A small number of files may still use raw generic types | These should be cleaned up as part of type-safety improvements |

### What this means

OpenMRS Core is not blocked by infrastructure currency; it is blocked by uneven adoption of the capabilities already available in its current platform baseline. The modernisation plan should therefore prioritise:

- high-value Java language and API improvements with low behavioural risk,
- compatibility-sensitive refactors behind strong test coverage,
- a container strategy designed for repeatable cloud deployment,
- Azure infrastructure that is intentionally different for developer simplicity and production isolation.

## Modernisation objectives

### 1. Java language modernisation

Modernise the codebase from Java 8-era patterns to idiomatic Java 21 usage where it improves maintainability and operational confidence.

Priority themes:

- replace outdated patterns with clearer modern syntax where it improves readability,
- reduce dependence on deprecated or removed platform features,
- migrate appropriate date/time usage from legacy APIs to `java.time`,
- improve type-safety and immutability where practical,
- establish coding guidance so new contributions do not reintroduce Java 8-era patterns.

This work is evolutionary, not cosmetic. The aim is to make the code easier to change safely over time.

### 2. Containerisation and Azure Container Apps

Create a delivery path from local development containers to Azure-hosted environments that reflects actual operational needs.

The target operating model is:

| Environment | Goal | Characteristics |
| --- | --- | --- |
| Development | Fast setup and low operational overhead | Minimal networking complexity, simple developer onboarding, straightforward container startup |
| Production | Secure, supportable cloud deployment | Azure Container Apps in an isolated VNET with Application Gateway as the ingress point |

The plan should preserve OpenMRS operational requirements such as database connectivity, persistent application data, health checks, and environment-specific configuration, while avoiding unnecessary platform complexity in development.

## Plan structure

The following documents make up the full modernisation plan:

| Document | Covers |
| --- | --- |
| [`01-java-language-modernisation.md`](./01-java-language-modernisation.md) | Java 8 to Java 21 coding modernisation: target patterns, refactoring priorities, standards, and constraints |
| [`02-containerisation.md`](./02-containerisation.md) | Container strategy, image design, Dockerfile updates, runtime configuration, and packaging decisions for cloud readiness |
| [`03-azure-infrastructure.md`](./03-azure-infrastructure.md) | Azure environment design for development and production, including Azure Container Apps, networking, ingress, and isolation |
| [`04-testing-strategy.md`](./04-testing-strategy.md) | Test approach for refactoring confidence, container validation, database compatibility, and release safety |
| [`05-migration-phases.md`](./05-migration-phases.md) | End-to-end sequencing, dependencies, milestones, and rollout phases across language, container, and infrastructure work |

## Diagramming

Architecture and infrastructure diagrams are maintained in **Lucidchart** as the single source of truth for visual documentation. Text-based summaries are kept in the markdown files for quick reference, but the authoritative diagrams live in Lucidchart.

| Diagram | Lucidchart Link | Plan Reference |
|---|---|---|
| Dev Environment Architecture | _TBD — created in Phase 0_ | [03-azure-infrastructure.md § Dev Environment](./03-azure-infrastructure.md) |
| Production Environment Architecture | _TBD — created in Phase 0_ | [03-azure-infrastructure.md § Production Environment](./03-azure-infrastructure.md) |
| Production Network Topology (VNET, Subnets, NSGs) | _TBD — created in Phase 0_ | [03-azure-infrastructure.md § Networking](./03-azure-infrastructure.md) |
| CI/CD Pipeline Flow | _TBD — created in Phase 0_ | [02-containerisation.md § CI/CD Pipeline](./02-containerisation.md) |
| Phase Dependency Graph | _TBD — created in Phase 0_ | [05-migration-phases.md § Phase Overview](./05-migration-phases.md) |

## Delivery and tracking approach

Implementation should be tracked through GitHub Issues created from the companion documents. Each issue should:

- reference the relevant plan document and section,
- describe the specific scope and acceptance criteria,
- identify impacted modules and test expectations,
- note dependencies on earlier migration phases or infrastructure work.

This keeps the strategy documents stable while allowing delivery work to be broken into reviewable, incremental changes.

## Key risks and mitigations

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Large-scale refactors introduce behavioural regressions | Java modernisation will touch widely used core APIs and legacy code paths | Phase changes by module and risk level; require targeted tests before and after refactors |
| `java.util.Date` migration becomes broader than expected | Date/time handling often crosses persistence, API, and UI boundaries | Prioritise boundary analysis first; migrate by domain area; use adapters where immediate full conversion is too risky |
| `SecurityManager` removal work exposes hidden dependencies | The existing helper indicates at least some code still depends on deprecated JVM mechanisms | Identify all call sites early and define a replacement approach before removing the class |
| Container changes optimise for cloud but slow local development | Developer friction can reduce adoption and feedback quality | Keep a simple dev path as an explicit requirement, not a side effect |
| Azure production networking becomes overly complex | ACA, private networking, and controlled ingress can add operational overhead quickly | Separate dev and prod designs; keep production isolation requirements explicit and justified |
| Database portability regresses during modernisation | OpenMRS supports more than one database backend | Validate against MariaDB, MySQL, and PostgreSQL in the testing strategy |
| CI pipeline drift delays delivery | Bamboo remains the active CI mechanism for this repository | Align changes with Bamboo Specs and update pipeline steps alongside build/container changes |
| Scope expands into unrelated platform rewrites | Modernisation efforts can become open-ended if not bounded | Keep scope anchored to the documented goals, phases, and issue-level acceptance criteria |

## Success criteria

The plan will be considered successful when the following outcomes are achieved:

| Outcome | Success signal |
| --- | --- |
| Java 21 is reflected in the code, not just the build configuration | Agreed modern Java patterns are adopted in actively maintained areas of the codebase and legacy-only patterns are reduced |
| Compatibility risks are retired | `SecurityManager` dependence is addressed and other obsolete constructs are removed or isolated |
| Date/time handling is improved | High-value areas have moved from legacy date APIs to `java.time`, with documented boundaries for any remaining legacy usage |
| Container delivery is cloud-ready | The image and runtime model support repeatable deployment beyond local development |
| Azure environments are clearly defined | Development and production deployment models are documented, with production isolation and ingress architecture agreed |
| Quality gates remain credible | Tests, CI, and validation steps are updated to support safe incremental change |
| Work is executable | GitHub Issues exist for concrete implementation items and trace back to this plan and its companion documents |

## Out of scope for this overview

This overview does not define low-level implementation details, exact issue breakdowns, or final Azure resource naming and provisioning scripts. Those decisions belong in the companion documents and the follow-on GitHub Issues.

## Related documentation notes

- The root repository `README.md` currently still describes Java 8 as the minimum version for building the main branch. That appears out of date relative to the current Java 21 build baseline and should be reviewed separately to avoid README drift.
