# Migration Phases

This document ties together the two parallel OpenMRS Core modernisation tracks:

- [Java Language Modernisation](./01-java-language-modernisation.md)
- [Containerisation](./02-containerisation.md)
- [Azure Infrastructure](./03-azure-infrastructure.md)
- [Testing Strategy](./04-testing-strategy.md)
- [Project Overview](../../README.md)

Planning baseline for this document:

- OpenMRS Core `3.0.0-SNAPSHOT`
- Java 21 runtime already works, but much of the code still follows Java 8-era patterns
- Local Docker support exists today, but production-grade container delivery and Azure deployment do not
- CI is split between Bamboo and existing GitHub Actions workflows; Phase 0 makes GitHub Actions the required path for this programme
- The codebase includes a large `java.util.Date` footprint and a broad automated test suite, so all changes must stay test-first and incremental

## 1. Phase Overview

The programme is intentionally split into two tracks that can progress in parallel after the foundation work is complete:

- **Track A: Java language modernisation**
  - Phase 0 → Phase 1 → Phase 3 → Phase 5
- **Track B: Container and Azure infrastructure**
  - Phase 0 → Phase 2 → Phase 4
- **Convergence**
  - Phase 6 requires both tracks to be complete enough for production cutover

### Visual sequence

```text
Foundation
  Phase 0: CI, standards, review process, baseline metrics, Azure tenancy setup
       │
       ├────────────── Java modernisation track ──────────────┐
       │                                                      │
       └──► Phase 1: Low-risk Java changes ─► Phase 3: Medium-risk Java changes ─► Phase 5: High-risk cleanup
       │
       └──────────── Infrastructure track ────────────────────┐
                                                              │
           ► Phase 2: Container + dev environment ─► Phase 4: Production infrastructure
                                                              │
                                                              └──────────────► Phase 6: Production migration and go-live
```

> 📐 **Lucidchart**: _Phase Dependency Graph — link TBD, created in Phase 0._
> The text diagram above is a simplified summary. The Lucidchart diagram includes swimlanes and milestone markers.

## 2. Detailed Phase Breakdown

### Phase 0: Foundation

**Purpose:** establish the delivery guardrails that every later phase depends on.

**Scope**

- Promote GitHub Actions from “available” to “programme-standard” CI for modernisation work
- Define Java 21 usage standards so refactors stay consistent across modules
- Update pull request guidance and reviewer checklists for incremental modernisation
- Establish test and quality baselines before wide code changes begin
- Prepare Azure subscription and resource group structure for dev and prod

**Key work items**

- Harden `.github/workflows/build.yaml` so it is the required validation path for modernisation PRs
- Decide whether Bamboo remains as a temporary parallel signal or is fully retired later
- Create a Java 21 coding standards document that defines when to use:
  - `var`
  - pattern matching
  - switch expressions
  - records
  - sealed classes
  - `java.time`
- Extend `.github/PULL_REQUEST_TEMPLATE.md` with modernisation-specific checks
- Capture baseline values for:
  - test pass rate
  - line/branch coverage
  - SpotBugs findings
  - container image size
  - startup time
  - latency for key endpoints
- Create Azure resource groups and naming conventions for dev and prod

**Deliverables**

- GitHub Actions CI pipeline for all required build and test stages
- Java 21 coding standards document approved by maintainers
- PR template and reviewer checklist for modernisation PRs
- Baseline quality and performance metrics recorded
- Azure subscription, resource groups, and access model ready
- Lucidchart architecture diagrams for dev and production Azure environments
- Lucidchart phase dependency and CI/CD pipeline diagrams

**Exit criteria**

- GitHub Actions runs the full required test suite for modernisation work
- Coding standards are approved and referenced by the team
- PR template/checklist is in use
- Baseline metrics are recorded and visible to the programme team
- Azure subscription and resource groups are available for Phase 2

**GitHub Issues**

- `Epic: Phase 0 - Foundation for OpenMRS Core Modernisation`
- `Set GitHub Actions as the required CI gate for modernisation branches`
- `Document Java 21 coding standards for OpenMRS Core`
- `Update PR template and review checklist for modernisation work`
- `Capture baseline test coverage, SpotBugs, startup, and latency metrics`
- `Provision Azure subscription access and resource groups for dev and prod`
- `Create Lucidchart architecture diagrams for dev and production Azure environments`
- `Create Lucidchart phase dependency and CI/CD pipeline diagrams`

---

### Phase 1: Low-Risk Java Modernisation

**Purpose:** deliver broadly safe Java 9-21 syntax and library improvements without changing public behaviour.

**Depends on:** Phase 0  
**Can run in parallel with:** Phase 2

**Scope**

Priority 1 language and library changes:

- Pattern matching for `instanceof`
- `var` for local variables
- Switch expressions
- Text blocks for multiline strings
- String API improvements such as `isBlank()`, `strip()`, and related replacements
- Collections factory methods such as `List.of()` and `Map.of()`
- Try-with-resources improvements

**Module order**

Apply changes in this order to contain risk and simplify review:

1. `tools`
2. `test`
3. `liquibase`
4. `api`
5. `web`
6. `webapp`

`bom` and `test-suite` remain supporting modules for validation and dependency alignment, but not the primary first-wave targets for syntax refactors.

**Execution model**

- One feature category at a time
- One pull request per module per feature category
- Prefer mechanical, behaviour-preserving changes
- No opportunistic redesigns in the same PR

**Deliverables**

- Reviewed PR set for each module and feature category
- Updated code following the approved Java 21 standards
- No regression in test results, formatting, or static analysis

**Exit criteria**

- All tests pass for every merged PR
- No SpotBugs regression relative to Phase 0 baseline
- Reviewer approval confirms the change is behavioural no-op unless explicitly documented
- Phase 1 module sequence is complete

**GitHub Issues**

- `Epic: Phase 1 - Low-Risk Java Modernisation`
- `Apply pattern matching for instanceof in tools module`
- `Apply var usage guidelines in tools module`
- `Replace legacy switch statements with switch expressions in tools module`
- `Adopt text blocks for multiline literals in test and liquibase modules`
- `Replace legacy String trimming and blank checks with modern String APIs`
- `Adopt collection factory methods in api, web, and webapp modules`
- `Simplify resource handling with modern try-with-resources patterns`
- `Track Phase 1 module rollout order and merge readiness`

---

### Phase 2: Container and Dev Environment

**Purpose:** turn the current local-development container setup into a repeatable dev deployment path on Azure.

**Depends on:** Phase 0  
**Can run in parallel with:** Phase 1

**Scope**

- Optimise the Dockerfile for production use
- Set up Azure Container Registry (ACR)
- Create a development Azure Container Apps environment without VNET complexity
- Deploy OpenMRS Core to the dev environment
- Validate the deployment with smoke tests

**Key work items**

- Replace dev-oriented image assumptions with production layering
- Use a JRE-focused runtime base image where compatible
- Improve Docker layer cache behaviour for Maven dependencies and WAR packaging
- Push versioned images to ACR
- Configure Container Apps revisions, ingress, secrets, and environment variables for dev
- Run smoke tests against dev after deployment

**Deliverables**

- Optimised Dockerfile suitable for CI-built release images
- Azure Container Registry
- Development Container Apps Environment
- Deployed dev instance of OpenMRS Core
- Smoke test checklist and results

**Exit criteria**

- CI builds and pushes the container image successfully
- Application runs in Azure Container Apps dev
- Health checks and smoke tests pass
- Deployment steps are documented and repeatable

**GitHub Issues**

- `Epic: Phase 2 - Containerisation and Dev Azure Environment`
- `Optimise Dockerfile for production image size and cache efficiency`
- `Create Azure Container Registry for OpenMRS Core images`
- `Provision Azure Container Apps dev environment`
- `Deploy OpenMRS Core to Azure Container Apps dev`
- `Add smoke tests for Container Apps dev deployment`
- `Document image tagging and promotion strategy for dev releases`

---

### Phase 3: Medium-Risk Java Modernisation

**Purpose:** deliver higher-value Java 21 adoption that needs stronger design review and compatibility checks.

**Depends on:** Phase 1

**Scope**

Priority 2 changes:

- Records for DTOs and value objects
- Sealed classes for genuinely closed hierarchies
- `java.time` migration
  - **Sub-phase A:** internal API first
    - private methods
    - package-private helpers
    - internal classes
  - **Sub-phase B:** public API migration
    - breaking change analysis
    - version bump strategy
    - downstream compatibility review
- Stream API modernisation where it improves readability and safety

**Execution model**

- Start with internal refactors that do not change public signatures
- Track every `java.util.Date` migration candidate and its dependency impact
- Validate serialization compatibility for records and any changed DTO shapes
- Require explicit maintainer approval before public API date/time changes merge

**Deliverables**

- PRs per module for records, sealed classes, stream updates, and `java.time` conversion
- Migration tracking spreadsheet or equivalent issue-based tracker for `java.util.Date` usage
- Compatibility notes for any public API impact

**Exit criteria**

- All tests pass
- Serialization compatibility is verified where DTOs are affected
- Internal `java.time` migration plan is complete and public API strategy is approved
- No public API change merges without agreed release/versioning approach

**GitHub Issues**

- `Epic: Phase 3 - Medium-Risk Java Modernisation`
- `Identify DTO and value object candidates for record conversion`
- `Convert low-risk DTOs to records in internal modules`
- `Identify closed type hierarchies suitable for sealed classes`
- `Migrate internal date/time handling from java.util.Date to java.time`
- `Prepare public API java.time migration proposal and versioning strategy`
- `Modernise targeted Stream API usage for readability and safety`
- `Create and maintain Phase 3 date/time migration tracker`

---

### Phase 4: Production Infrastructure

**Purpose:** evolve the proven dev deployment into a production-grade Azure architecture.

**Depends on:** Phase 2

**Scope**

- Provision production VNET and subnets
- Deploy Application Gateway v2 with WAF
- Create private endpoints for required PaaS services
- Deploy Container Apps into the VNET-integrated environment
- Configure managed identities
- Apply security hardening
- Set up monitoring and alerting

**Key work items**

- Define subnet plan for ingress, Container Apps, and private endpoints
- Lock down public access to dependent services wherever possible
- Use managed identities instead of stored credentials
- Configure log aggregation, metrics, dashboards, and alert routing
- Run security review before production readiness sign-off

**Deliverables**

- Production Azure network and Container Apps environment
- Application Gateway v2 with WAF
- Private endpoint-enabled service connectivity
- Managed identity configuration
- Monitoring dashboards and alert rules
- Security review output and remediation list

**Exit criteria**

- Application is reachable through Application Gateway
- All planned security controls are verified
- Smoke tests pass in the production-like environment
- Monitoring and alerting are active and tested
- Security review is passed or approved with documented residual risk

**GitHub Issues**

- `Epic: Phase 4 - Production Azure Infrastructure`
- `Provision production VNET and subnet layout for Container Apps`
- `Deploy Application Gateway v2 with WAF for OpenMRS Core`
- `Create private endpoints for production PaaS dependencies`
- `Deploy production Container Apps environment inside VNET`
- `Configure managed identities and secret access model`
- `Implement production monitoring, dashboards, and alerts`
- `Run production security hardening review and remediation`

---

### Phase 5: High-Risk Changes and Cleanup

**Purpose:** complete the riskiest application-level changes after lower-risk modernisation is stable.

**Depends on:** Phase 3

**Scope**

- SecurityManager removal
- HttpClient migration
- Final cleanup of deprecated APIs and transitional code
- Before/after performance benchmarking

**Key work items**

- Decide how module security and sandboxing work without `SecurityManager`
- Replace legacy HTTP client usage with the agreed modern client approach
- Remove temporary compatibility shims introduced in earlier phases
- Benchmark startup time, throughput, latency, and resource usage against the Phase 0 baseline

**Deliverables**

- SecurityManager replacement design and implementation
- Migrated HTTP client usage
- Cleaned codebase with deprecated API usage removed where targeted
- Performance report comparing baseline vs final modernised state

**Exit criteria**

- No targeted deprecated API usage remains
- Security design is approved and implemented
- Performance stays within agreed acceptable thresholds
- Any behaviour changes are documented and signed off

**GitHub Issues**

- `Epic: Phase 5 - High-Risk Java Changes and Cleanup`
- `Define post-SecurityManager module security model for OpenMRS Core`
- `Remove SecurityManager usage and implement approved replacement controls`
- `Migrate legacy HTTP client usage to modern Java HTTP client`
- `Remove transitional deprecated APIs introduced during modernisation`
- `Benchmark startup time, latency, and resource usage before and after modernisation`

---

### Phase 6: Production Migration and Go-Live

**Purpose:** cut over to the Azure-hosted production platform with operational readiness in place.

**Depends on:** Phase 4 and Phase 5

**Scope**

- DNS cutover planning
- Data migration if required
- Blue-green deployment
- Monitoring validation
- Runbook creation and review

**Key work items**

- Define go/no-go criteria
- Prepare rollback steps
- Verify blue-green release flow and revision promotion
- Validate dashboards, alerts, and on-call routing during live cutover
- Finalise runbooks for deployment, rollback, incident response, and routine operations

**Deliverables**

- Production deployment on Azure
- DNS cutover plan and rollback plan
- Blue-green deployment procedure
- Runbooks for operations, rollback, and incident response

**Exit criteria**

- Application is running in production Azure
- Monitoring and alerting are active and validated
- Runbooks are reviewed by engineering and operations stakeholders
- Cutover and rollback procedures are tested or rehearsed

**GitHub Issues**

- `Epic: Phase 6 - Production Migration and Go-Live`
- `Create DNS cutover and rollback plan for Azure go-live`
- `Assess and execute application data migration requirements`
- `Validate blue-green deployment process for production cutover`
- `Validate production monitoring and alert routing during rehearsal`
- `Write and review deployment, rollback, and incident runbooks`

## 3. Phase Dependencies Diagram

```text
Phase 0 ──┬──► Phase 1 ──► Phase 3 ──► Phase 5 ──┐
           │                                        ├──► Phase 6
           └──► Phase 2 ──► Phase 4 ───────────────┘
```

> 📐 **Lucidchart Diagram**: [Phase Dependency Graph — _link TBD, created in Phase 0_]
> The text diagram above is a simplified summary. See the Lucidchart diagram for the authoritative view with swimlanes and milestone markers.

### Dependency notes

- **Phase 0 is mandatory** for both tracks.
- **Phase 1 and Phase 2 can run in parallel** once the CI, standards, and Azure setup are ready.
- **Phase 3 depends on Phase 1** because records, sealed classes, and `java.time` should not start until low-risk syntax refactors have stabilised.
- **Phase 4 depends on Phase 2** because production Azure design should reuse the dev deployment learnings.
- **Phase 5 depends on Phase 3** because high-risk cleanup should happen after the medium-risk Java changes settle.
- **Phase 6 depends on both Phase 4 and Phase 5** because production cutover requires both application readiness and infrastructure readiness.

## 4. Risk Register

| Phase | Key risks | Mitigation | Human approval required |
| --- | --- | --- | --- |
| Phase 0 | CI drift between Bamboo and GitHub Actions causes conflicting signals | Make GitHub Actions the required gate for modernisation work, document Bamboo role explicitly, and compare outputs during transition | Whether Bamboo is retained temporarily or retired immediately |
| Phase 0 | Team applies Java 21 features inconsistently | Approve a coding standards document before Phase 1 merges | Coding standards approval |
| Phase 1 | Large-scale mechanical refactors create noisy PRs and hidden regressions | Keep PRs small, single-purpose, and module-scoped; require full CI and reviewer checklist | None beyond normal code review |
| Phase 1 | Overuse of `var` or switch expressions reduces readability | Define usage rules and examples in the standards document | Exceptions to standards if needed |
| Phase 2 | Container image remains too large or slow to build | Baseline image size, optimise layers, and fail builds on major regressions if practical | Base image choice for production |
| Phase 2 | Dev Azure deployment works, but configuration is not production-portable | Keep environment-specific settings isolated and document assumptions | Whether to proceed to production design with current dev architecture |
| Phase 3 | Record or sealed class changes break serialization or framework integration | Convert only vetted candidates first and add compatibility tests | Approval for each public or serialized type change |
| Phase 3 | `java.time` migration breaks public APIs or downstream modules | Split internal and public API work, publish versioning strategy before public changes | Public API migration plan and version bump strategy |
| Phase 4 | Production network topology becomes too complex or costly | Review architecture early, stage VNET and private endpoint rollout, validate with threat model and cost checks | Production network and security architecture approval |
| Phase 4 | Security controls delay release late in the programme | Run security review before final hardening, not after deployment | Security review sign-off |
| Phase 5 | SecurityManager removal leaves an unresolved security gap | Make the replacement model a design-first task, not a pure refactor | Security model decision |
| Phase 5 | HttpClient migration changes timeout, proxy, or TLS behaviour | Introduce compatibility tests and compare runtime behaviour in non-prod first | Any behavioural deviation from current client behaviour |
| Phase 6 | Cutover introduces downtime or rollback risk | Rehearse blue-green flow, validate rollback, freeze unrelated changes during go-live | Final go/no-go decision |
| Phase 6 | Operational team lacks clear runbooks | Review runbooks before launch and run tabletop exercises | Runbook approval by engineering and operations |

## 5. Success Metrics

Baseline values are captured in Phase 0 and measured again at the end of each later phase.

| Metric | Baseline source | Target / threshold | Notes |
| --- | --- | --- | --- |
| Test pass rate | Phase 0 CI baseline | **100% must remain** | Required for every phase exit |
| Code coverage | Phase 0 coverage baseline | **Must not decrease** | Any exception needs explicit approval |
| SpotBugs findings | Phase 0 static analysis baseline | No net regression | Especially important for large mechanical refactors |
| Java 21 feature adoption | Phase 0 inventory by feature category | Increase each phase against tracked backlog | Track by issue completion and code inventory |
| `java.util.Date` usage | Phase 0 inventory | Downward trend in Phase 3, targeted removals complete by Phase 5 scope | Separate internal and public API counts |
| Container image size | Phase 0 container baseline | Smaller than current production-style image baseline; target at least meaningful reduction | Exact threshold set in Phase 0 |
| Deployment time | First measured in Phase 2 | Stable, repeatable deployment time for dev and prod | Track CI-to-running-app duration |
| Application startup time | Phase 0 runtime baseline | No unacceptable regression; improvement preferred | Measure after container and Java changes |
| Response latency | Phase 0 smoke/perf baseline | No unacceptable regression in key endpoints | Use p95 for comparison where possible |

## 6. GitHub Issues Tracking

Use GitHub Issues as the execution layer for this plan.

### Recommended issue structure

- **One epic per phase**
  - Example: `Epic: Phase 3 - Medium-Risk Java Modernisation`
- **Stories/tasks inside each epic**
  - One task for each concrete deliverable or module rollout
- **Each issue links back to the plan**
  - Use a plan reference in the issue description
- **Each issue uses consistent labels**
  - `modernisation`
  - `java-21`
  - `azure`
  - `infrastructure`
  - `testing`

### Reference format

Use this format in issue descriptions:

```text
[Plan: 01-java-language-modernisation.md#section-name]
```

Examples:

- `[Plan: 01-java-language-modernisation.md#priority-1-features]`
- `[Plan: 02-containerisation.md#image-build-strategy]`
- `[Plan: 03-azure-infrastructure.md#production-topology]`
- `[Plan: 04-testing-strategy.md#regression-gates]`
- `[Plan: 05-migration-phases.md#phase-3-medium-risk-java-modernisation]`

### Label guidance by track

| Work type | Labels |
| --- | --- |
| Java syntax and API modernisation | `modernisation`, `java-21` |
| Container build and image work | `modernisation`, `azure`, `infrastructure` |
| Azure networking and runtime services | `modernisation`, `azure`, `infrastructure` |
| Test baseline, smoke tests, regression gates | `modernisation`, `testing` |

## Companion documents

- [README.md overview](../../README.md)
- [01-java-language-modernisation.md](./01-java-language-modernisation.md)
- [02-containerisation.md](./02-containerisation.md)
- [03-azure-infrastructure.md](./03-azure-infrastructure.md)
- [04-testing-strategy.md](./04-testing-strategy.md)
