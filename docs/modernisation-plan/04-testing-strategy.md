# Testing Strategy

See also:

- [Repository README](../../README.md)
- [01 - Java language modernisation](./01-java-language-modernisation.md)
- [02 - Containerisation](./02-containerisation.md)
- [03 - Azure infrastructure](./03-azure-infrastructure.md)

## Purpose

This document defines how OpenMRS core modernisation work will be verified across three tracks:

1. Java language modernisation
2. Containerisation
3. Azure infrastructure

The project already starts from a strong testing baseline: JUnit Jupiter 6.0.3, Mockito 5.23.0, Testcontainers 2.0.4, H2 2.3.232, DBUnit 3.0.0, Hamcrest 3.0, SpotBugs, Checkstyle, Spotless, JaCoCo, and automated Maven test execution in CI. Planning assumptions reference 359 test files across 7 Maven modules; in the current repository, tests are heavily concentrated in `api`, `web`, `liquibase`, and `test-suite`, with integration tests following the `*IT.java` naming convention and shared web test base classes under `web/src/test/java/org/openmrs/web/test/`.

The key point for this modernisation is simple: most Java refactoring should not change behaviour, so the existing automated test suite is the primary safety net. New runtime and infrastructure work needs additional black-box and deployment-focused checks.

Phase 0 testing foundations are now present in the repository:

- GitHub Actions modernisation pipeline: [`../../.github/workflows/ci-modernisation.yml`](../../.github/workflows/ci-modernisation.yml)
- JaCoCo build configuration: root [`../../pom.xml`](../../pom.xml)
- Baseline metrics reference: [`../baseline-metrics.md`](../baseline-metrics.md)
- Baseline capture automation: [`../../tools/capture-baseline-metrics.sh`](../../tools/capture-baseline-metrics.sh)

## 1. Testing principles for the modernisation

### Core principles

- **Zero regression tolerance:** all existing tests must continue to pass.
- **Behaviour preservation:** Java language modernisation is refactoring work, not feature work; any test failure is treated as a possible behavioural change until proven otherwise.
- **Progressive delivery:** modernise one module or bounded area at a time, then re-run the relevant test layers before moving on.
- **Production-like verification for platform changes:** container and Azure changes must be validated with runtime checks, not only compile-time checks.
- **No merge without green CI:** every modernisation PR must pass the full required test suite before merge.

### Quality gates

At minimum, every modernisation PR must pass:

1. Compile and package successfully.
2. Unit and component tests for affected modules.
3. Repository integration-test profiles.
4. Static analysis and formatting checks.
5. Coverage reporting with no reduction from the current baseline.

For container and infrastructure work, PR or pre-release gates also include:

1. Container build success.
2. Health check success in a running container.
3. Smoke tests against the deployed environment.
4. Security and vulnerability checks for the produced image and deployment configuration.

## 2. Java language modernisation testing

### Scope under test

This track covers refactoring to newer Java features and APIs, including:

- records
- sealed classes
- pattern matching
- `var`
- text blocks
- switch expressions
- `java.util.Date` to `java.time` migration
- SecurityManager removal

### Regression testing strategy

The preferred delivery model is **module-by-module modernisation**:

1. Update one module or one tightly related package at a time.
2. Run module-local tests immediately.
3. Run the full repository test suite after each completed module slice.
4. Merge only after CI confirms the full suite is green.

Because these changes are mostly structural, **existing tests should remain sufficient for most refactoring**. New tests are only required where a refactor changes generated methods, object shape, serialisation boundaries, or runtime bootstrapping behaviour.

### Focus areas that may require test updates

#### Record conversion

When converting eligible classes to records, review and test:

- `equals()`
- `hashCode()`
- `toString()`
- serialisation/deserialisation behaviour
- framework binding assumptions

Specific expectation:

- If a class already has custom `equals()` or `hashCode()` semantics, do not assume record defaults are equivalent.
- Add or update tests before conversion when equality semantics are business-significant.

#### SecurityManager removal

`api/src/test/java/org/openmrs/util/OpenmrsSecurityManagerTest.java` is a known hotspot. Removing `SecurityManager` usage may affect:

- startup behaviour
- class loading assumptions
- module loading
- legacy security checks

Specific expectation:

- Update this test area early in the workstream.
- Treat any failures here as release-blocking until the replacement design is understood and covered.

#### `java.util.Date` to `java.time` migration

This refactor can break behaviour at boundaries even when core logic still compiles. Review and test:

- JSON and XML serialisation
- persistence mappings
- timezone handling
- truncation and precision
- comparisons in existing assertions

Specific expectation:

- Any test that currently asserts directly on `Date`, timestamps, or formatted strings may need to move to explicit `java.time` assertions.
- Add explicit serialisation tests where date/time objects cross REST, XML, or database boundaries.

### Static analysis as a safety net

Static checks should be treated as part of regression protection, not as optional extras.

#### SpotBugs

- The repository already runs SpotBugs, but `failOnError=false` means the immediate goal is **no new findings introduced by modernisation**.
- Track findings on a diff basis if a full clean slate is not yet practical.
- Any new high-confidence bug pattern introduced by Java refactoring must block merge.

#### Checkstyle

- Checkstyle rules may need updates for newer Java syntax.
- Records, sealed hierarchies, pattern matching, and text blocks should be validated against current rules before broad rollout.
- If rules lag behind supported syntax, update the rules intentionally rather than suppressing violations ad hoc.

#### Spotless

- Spotless uses the Eclipse formatter configuration in `tools/src/main/resources/eclipse/OpenMRSFormatter.xml`.
- Validate formatter output for records, sealed classes, and text blocks before large-scale automated refactors.
- Formatter or import-order updates should be applied once and then reused consistently across all modernised modules.

### Coverage expectations

- Maintain the current coverage level; do not allow coverage to decrease because code was modernised.
- New constructs such as records and sealed classes should have equivalent effective coverage to the classes they replace.
- JaCoCo reporting remains the coverage source of truth.
- The JaCoCo plugin configuration now lives in the root [`../../pom.xml`](../../pom.xml), so coverage checks should be treated as part of the default build guardrails.
- Use **non-regression coverage gating** against the main branch baseline rather than chasing arbitrary percentage gains during refactoring-only PRs.

### Recommended execution flow

For each Java modernisation slice:

```bash
./mvnw test
./mvnw test -Pintegration-test -Pskip-default-test
./mvnw test -Pperformance-test -Pskip-default-test
```

Use the performance profile selectively for changes that can affect startup, reflection, date/time handling, or framework bootstrapping.

## 3. Containerisation testing

Container work must prove that the packaged runtime behaves the same way as the current application and starts reliably in CI and in Azure-hosted environments.

### Container image testing

Each container-related PR should verify that:

- the image builds successfully in CI
- image size stays within expected bounds for the chosen base image and packaging strategy
- vulnerability scanning passes with **no critical or high CVEs**
- the container starts successfully within the agreed timeout
- the health check passes
- the application responds on port `8080`

Minimum runtime smoke checks:

- `GET /openmrs/health/alive` returns `200`
- the web application is reachable under `/openmrs`
- logs show successful startup rather than repeated crash-loop behaviour

### Docker Compose testing

For local and CI validation, `docker compose` should prove the application can boot with its dependencies from a clean state.

Required checks:

- `docker compose up` succeeds from a clean environment
- the application connects to the configured database
- health endpoints respond
- basic CRUD operations work
- shutdown and restart do not leave the environment in a broken state

Recommended scenarios:

1. Fresh build and startup
2. Restart using existing volumes
3. Startup after database schema migration
4. Optional profiles such as ElasticSearch or Grafana where relevant to the change

### Container quality gates

- No merge if the image fails to build.
- No merge if the application does not become healthy within timeout.
- No merge if vulnerability scanning reports critical or high issues without an approved exception.
- No promotion if Compose-based smoke tests fail against the release candidate image.

## 4. Infrastructure testing

Infrastructure changes must be verified at two levels:

1. **Provisioning correctness** — the Azure resources are created with the intended topology and policies.
2. **Application operability** — the deployed OpenMRS container is reachable, healthy, and connected to required dependencies.

### Dev environment

The development environment should validate the simpler public deployment path first.

Required checks:

- Bicep deployment succeeds
- Container App deploys successfully
- health checks pass after deployment
- the application is accessible via the public FQDN
- the Container App can connect to the database
- required secrets are accessible from Key Vault

This environment is the first place to validate:

- image startup behaviour in Azure
- environment variable and secret wiring
- health probe paths
- ingress configuration

### Production environment

Production verification focuses on network controls, routing, and resilience.

Required checks:

- VNET is provisioned with the correct subnets
- Application Gateway routes traffic to the Container App correctly
- WAF rules are enabled and active
- private endpoints resolve correctly
- backend resources are not publicly accessible
- SSL/TLS termination works correctly
- Application Gateway health probes succeed
- scaling respects configured minimum and maximum replicas

Additional production concerns to validate:

- DNS and certificate rollover behaviour
- rollback procedure for failed deployments
- observability coverage for health, logs, and alerts

### Smoke tests post-deployment

Every deployment to dev, staging, or production should run a minimal black-box smoke suite.

Required smoke tests:

- `GET /openmrs/health/alive` returns `200`
- login POST with admin credentials succeeds
- basic REST API operations succeed
- Liquibase migrations completed successfully

Recommended smoke suite order:

1. Liveness check
2. Login/authentication check
3. Read-only API check
4. Basic create/update/delete check against a safe test entity or fixture
5. Database migration verification

### Suggested smoke test implementation

Keep the first version simple:

- use GitHub Actions jobs that run after deployment
- use environment-scoped secrets
- use `curl` for health and simple endpoint checks
- use a lightweight API test layer for authenticated flows and CRUD validation

A pragmatic implementation path is:

1. start with shell-based smoke checks for health, login, and a small REST flow
2. move to a dedicated smoke-test suite if the scenarios become more complex
3. reuse the same smoke suite across dev, staging, and production with environment-specific configuration only

## 5. Test automation updates

### Existing automation

The repository already has strong automation foundations:

- Maven Surefire 3.5.5 for unit test execution
- Maven-driven integration-test automation for `*IT.java`, `*DatabaseIT.java`, and `*PerformanceIT.java`
- Testcontainers-based database tests
- The dedicated modernisation workflow at [`../../.github/workflows/ci-modernisation.yml`](../../.github/workflows/ci-modernisation.yml)
- JaCoCo coverage reporting configured in the root [`../../pom.xml`](../../pom.xml)
- SpotBugs, Checkstyle, and Spotless in the Maven build

The `pom.xml` still references Bamboo as the CI system of record, so the modernisation should treat GitHub Actions as the active implementation path and Bamboo metadata/processes as cutover items to clean up deliberately.

### New or expanded automation needed

#### GitHub Actions as the primary modernisation gate

Extend the existing workflows so that modernisation work always runs:

The current implementation anchor for this is [`../../.github/workflows/ci-modernisation.yml`](../../.github/workflows/ci-modernisation.yml).

- unit tests
- integration tests
- coverage publishing
- static analysis
- container build validation

#### Container build and scan

Add or expand CI jobs to:

- build the application image
- scan the image for vulnerabilities
- fail on critical/high findings
- run a startup smoke test against the built image

#### Infrastructure deployment smoke tests

For Azure deployment workflows:

- validate Bicep templates before deployment
- deploy to dev/staging
- run post-deployment smoke tests automatically
- block promotion if smoke tests fail

#### Post-deployment smoke test suite

Suggested approach:

- keep the smoke tests external to the application runtime
- treat them as black-box checks
- drive them from GitHub Actions with per-environment secrets and URLs
- keep them fast enough to run on every deployment

#### Performance baseline tests

The repository already includes performance-oriented coverage such as `test-suite/performance/src/test/java/org/openmrs/StartupPerformanceIT.java`.

Use that as the starting point for a before/after baseline:

The baseline definition and capture process are now documented in [`../baseline-metrics.md`](../baseline-metrics.md) and automated by [`../../tools/capture-baseline-metrics.sh`](../../tools/capture-baseline-metrics.sh).

- capture startup time before major modernisation waves
- compare startup time after Java changes
- compare cold-start behaviour for container images
- compare Azure deployment readiness times after infra changes

The goal is not exhaustive load testing during every PR; it is to detect accidental regressions in startup and readiness.

## 6. Risk matrix

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `java.time` migration breaks serialization | Medium | High | Test JSON/XML serialization explicitly |
| Record conversion changes `equals()`/`hashCode()` | Medium | Medium | Review all overridden `equals()`/`hashCode()` before converting |
| SecurityManager removal breaks module system | High | High | Investigate `OpenmrsSecurityManager` usage thoroughly |
| Container image fails in Azure | Low | High | Test locally and in a staging/dev Azure environment first |
| App Gateway health probes fail | Low | Medium | Validate health check path and response before promotion |

## 7. Definition of done for modernisation testing

A modernisation work item is only done when:

- code changes are merged with a green CI run
- no existing automated tests regressed
- any required test updates for records, `java.time`, or SecurityManager removal are committed with the change
- coverage did not regress
- static analysis did not introduce new issues
- container and deployment smoke tests pass for infrastructure-affecting changes

## 8. GitHub issues

Create GitHub issues for each testing work item, including:

- Java modernisation hotspot test updates
- formatter and rule updates for newer Java syntax
- container build and scan automation
- Azure deployment smoke tests
- performance baseline capture and comparison

Tracking each item separately keeps the testing backlog visible and lets implementation work progress independently of the main refactoring tasks.

## 9. Open gaps to resolve during delivery

The following gaps should be resolved as part of execution:

- decide whether integration-test execution remains on the current Surefire profile model or is migrated to Failsafe as a separate cleanup
- define the exact coverage threshold or non-regression rule to enforce in CI
- define the acceptable container startup timeout and image size budget
- choose the first implementation technology for post-deployment smoke tests
- document the exact login and REST smoke-test fixtures and credentials strategy per environment

Until those are finalised, the quality gates in this document should be treated as the minimum required direction.

---

## Tracked Issues

### Phase 0 — Foundation (Testing)

- [ ] #12 — Set up GitHub Actions CI pipeline for modernisation branches
- [ ] #45 — Capture baseline test coverage, SpotBugs, startup, and latency metrics
- [ ] #79 — Establish test coverage baseline with JaCoCo

### Phase 2 — Dev Environment Validation

- [ ] #89 — Create automated smoke test suite for post-deployment validation
- [ ] #92 — Validate dev deployment end-to-end (health, DB, Liquibase, login)

### Phase 3 — java.time Testing

- [ ] #74 — Add serialization round-trip tests for java.time types

### Phase 5 — Performance Benchmarking

- [ ] #104 — Performance benchmarking — compare against Phase 0 baseline

### Phase 6 — Go-Live Validation

- [ ] #13 — Pre-production validation — full regression suite in staging
- [ ] #42 — Load testing in production environment
