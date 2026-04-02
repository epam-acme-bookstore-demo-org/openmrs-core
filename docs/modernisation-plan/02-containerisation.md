# 02. Containerisation Strategy

Back to [README](../../README.md)  
Next: [03. Azure Infrastructure](03-azure-infrastructure.md) · [05. Migration Phases](05-migration-phases.md)

## Purpose

Define how OpenMRS Core should evolve from a developer-oriented Docker setup into a production-ready container delivery model for Azure Container Apps (ACA), without losing the strengths of the current implementation.

## Summary

OpenMRS Core is **already partially containerised**:

- the application is built in Docker with a multi-stage `Dockerfile`
- there is a production Tomcat image that runs as a non-root user
- there are health checks
- local development is supported through Docker Compose
- MariaDB, optional Elasticsearch, and optional Grafana are already available as containers for local development

For Azure deployments, the equivalent managed database target is **Azure Database for PostgreSQL Flexible Server**, not a containerised MariaDB service.

That is a strong starting point. The main gap is not "whether the app can run in containers"; it is that the current setup is still optimized for **local development and Docker Hub publishing**, not for **repeatable, secure, cloud deployment on Azure Container Apps**.

The target state is:

- a **lean production image** built once and promoted by digest
- **environment-specific configuration** supplied entirely at deploy time
- **secrets managed outside the image**
- **managed external dependencies** in Azure rather than bundled local containers
- a **GitHub Actions pipeline** that builds, scans, signs/attests, and pushes multi-platform images to Azure Container Registry (ACR)

---

## 1. Current container assessment

## What is already containerised

### Application image

The current `Dockerfile` already supports three useful stages:

1. **compile**: Maven + Temurin 21 build image
2. **dev**: Maven + Tomcat 11 image for local development
3. **production**: Tomcat 11 runtime image serving `openmrs.war`

### Local dependency containers

Current Compose files provide:

- **MariaDB 10.11.7** in `docker-compose.yml`
- **OpenMRS API/application container** in `docker-compose.yml`
- **Elasticsearch** in `docker-compose.es.yml`
- **Grafana/Loki/Alloy** in `docker-compose.grafana.yml`

These Compose dependencies remain local-development defaults. Azure environments use managed services, with **PostgreSQL** as the standard managed database target.

### Runtime startup automation

The startup scripts already support:

- database bootstrapping and connection setup
- environment-variable-driven configuration
- search backend selection (`lucene` or `elasticsearch`)
- WAR deployment into Tomcat at container start

This means the application already has the minimum contract needed for container platforms:

- one container process
- port `8080`
- health endpoint at `/openmrs/health/alive`
- externalised configuration via environment variables

## What is already done well

The current container setup has several production-friendly foundations that should be retained:

- **Multi-stage build** keeps compilation out of the runtime stage.
- **Non-root runtime user** (`USER 1001`) is already in place.
- **Tini** is used as the init process, which is appropriate for signal handling in containers.
- **Health checks** already exist in both dev and production images.
- **WAR-on-Tomcat deployment model** is explicit and stable.
- **Multi-platform builds already exist in Bamboo** through `docker buildx build --platform ...`.

## Gaps that block cloud-ready containerisation

### 1. The current image is still heavier than necessary

The production stage uses `tomcat:11-jdk21-temurin`. That is functional, but not yet optimized for:

- minimum image size
- lower CVE surface area
- faster cold starts

### 2. Development and production concerns are mixed

One `Dockerfile` serves compile, dev, and production use cases. That works, but it makes:

- CI configuration more complex
- production runtime requirements less obvious
- accidental drift between dev and prod easier

### 3. Compose is local-first, not environment-specific

The current Compose files are useful for developers, but they still include:

- hardcoded default passwords
- local persistence assumptions
- local-only service wiring

They are not a deployment model for Azure.

### 4. The runtime state model is not yet aligned with ACA

Local Compose uses named volumes, especially under `/openmrs/data`. Azure Container Apps containers are **ephemeral** by default. The strategy must explicitly decide what belongs in:

- the image
- a mounted persistent volume
- an external managed service

### 5. Registry, scanning, and promotion are not Azure-native yet

The repository currently publishes through Bamboo-oriented Docker workflows. For Azure, the target must be:

- Azure Container Registry
- Microsoft Defender vulnerability scanning
- GitHub Actions-based image promotion
- deployment by immutable digest, not by mutable tags

---

## 2. Container strategy for Azure Container Apps

## Deployment model

For ACA, OpenMRS should be deployed as a **single web application container** that:

- listens on `8080`
- serves the WAR through Tomcat 11
- reads all environment-specific configuration from environment variables and secrets
- treats the container filesystem as disposable except for explicitly mounted paths

Local dependency containers from Compose are **not** the production model. In Azure, the application container should consume managed services defined in [03. Azure Infrastructure](03-azure-infrastructure.md).

## State model for ACA

This is the most important containerisation decision.

### Keep in the image

Bake these into the production image:

- `openmrs.war`
- startup scripts
- default distribution artifacts that are part of the release

### Keep external to the container

Use Azure-managed services or mounted storage for:

- the application database
- any persistent search service, if Elasticsearch is selected
- persistent binary/file content under `/openmrs/data` that must survive restarts

### Prefer immutable application behaviour in production

To reduce runtime drift in ACA:

- **disable build-at-startup behaviour** in production
- **disable module upload/admin changes from the web UI** unless there is a defined persistence story
- **deploy new application versions by image release**, not by modifying a running container

Recommended production defaults:

- `OMRS_BUILD=false`
- `OMRS_MODULE_WEB_ADMIN=false`
- `OMRS_ADMIN_PASSWORD_LOCKED=true`

If runtime-installed modules must be supported, document and mount a persistent volume specifically for `/openmrs/data/modules`.

## Azure Container Apps runtime contract

ACA deployment should assume:

- **target port**: `8080`
- **ingress path base**: `/openmrs`
- **liveness probe**: `/openmrs/health/alive`
- **startup probe**: same endpoint initially, with a longer startup window to account for first boot, Liquibase, and Tomcat warm-up

### Readiness

Today, the documented health endpoint is `/openmrs/health/alive`. That is sufficient for liveness, but not ideal for readiness.

Action:

- keep `/openmrs/health/alive` for liveness
- create a follow-up issue to add a **readiness-specific endpoint** if ACA rollout behavior requires it

Until then, ACA probe timing must be generous enough to avoid restarting containers during database initialization or schema migration.

## Configuration management

The existing startup scripts already support a strong env-var contract. ACA should use that instead of image-specific config files.

### Application settings via environment variables

Use plain environment variables for non-secret configuration such as:

- `OMRS_DB`
- `OMRS_DB_HOSTNAME`
- `OMRS_DB_PORT`
- `OMRS_DB_NAME`
- `OMRS_DB_URL`
- `OMRS_SEARCH`
- `OMRS_SEARCH_ES_URIS`
- `OMRS_JAVA_SERVER_OPTS`
- `OMRS_JAVA_MEMORY_OPTS`

### Secrets

Use ACA secrets backed by **Azure Key Vault references** for:

- `OMRS_DB_USERNAME`
- `OMRS_DB_PASSWORD`
- `OMRS_ADMIN_USER_PASSWORD`
- any search credentials if Elasticsearch is secured
- any monitoring or dashboard credentials

Do not bake secrets into:

- Dockerfiles
- Compose files committed to the repository
- GitHub Actions YAML

### Database connection strategy

The container layer should stay database-agnostic because startup scripts already support:

- MariaDB
- MySQL
- PostgreSQL

The Azure infrastructure decision belongs in [03. Azure Infrastructure](03-azure-infrastructure.md), but the container contract should standardize on:

- `OMRS_DB_URL` when a full managed connection string is available
- secret-backed username/password variables
- TLS-enabled JDBC configuration where supported by the selected managed database

For Azure deployments, assume PostgreSQL-specific connection details unless a non-Azure environment explicitly chooses a different supported engine.

## Container registry

### Registry target

Use **Azure Container Registry (ACR)** as the system of record for deployable images.

### Pull model

Azure Container Apps should pull from ACR using **managed identity**, not static registry credentials.

### Tagging strategy

Publish at least these tags for each successful build:

- `X.Y.Z` for releases
- `X.Y.Z-<gitsha>` for release traceability
- `<branch>-<gitsha>` for non-release builds

Operational rules:

- deployments should use the **image digest**
- tags are for discovery and traceability, not rollout safety
- never deploy production from `latest`

### Scanning and policy

Enable:

- **Microsoft Defender for Containers / ACR scanning**
- CI-time image scanning before push or before promotion
- fail-or-warn thresholds that are explicit in the pipeline

---

## 3. Dockerfile improvements

## Recommendation: keep multi-stage, but separate developer ergonomics from production

The current multi-stage approach is sound. The next step should be one of these two options:

### Preferred

- `Dockerfile` for production
- `Dockerfile.dev` for local development

This makes the production artifact easier to reason about and reduces the chance of dev-only behavior leaking into CI/CD.

### Acceptable alternative

Keep one multi-stage file, but:

- rename stages to `builder`, `dev`, and `runtime`
- explicitly document which target is supported in CI
- treat `runtime` as the only deployable target for ACA

## Production image optimisation

### Move to a JRE-only runtime where practical

Current runtime:

- `tomcat:11-jdk21-temurin`

Target direction:

- prefer a **JRE-only Tomcat/runtime image**
- if an official Tomcat 11 JRE image is unavailable or unsupported, build a minimal runtime from:
  - a slim JRE 21 base image, plus
  - a pinned Tomcat distribution

This should reduce image size and shrink the vulnerability surface.

### Reduce runtime packages

The production image currently installs `curl` for health checks and downloads `tini` during build.

Improvements:

- minimize runtime package installation
- pin base images by digest
- copy only required binaries and scripts into runtime
- remove any tools that are useful only for debugging or building

If health checks can be handled by the platform instead of in-image tooling, consider removing `curl` from the runtime image entirely.

### Consider slim or distroless carefully

Two realistic options:

- **slim Linux base + Tomcat**: lower risk, easier operations
- **distroless Java runtime**: smaller and harder to tamper with, but harder to debug

Recommendation:

- use **slim/minimal images first**
- evaluate distroless only after startup scripts, volume mounts, and diagnostics are simplified

Because this application still relies on Tomcat plus shell-based startup orchestration, distroless is a second-phase optimisation, not the first move.

## Faster builds through better caching

The current Dockerfile already copies POM files before source files, which is good for Maven dependency reuse.

Further improvements:

- add a repository-level `.dockerignore` if not already present and exclude `target/`, `.git/`, local caches, and editor files
- use BuildKit cache mounts for Maven dependencies in CI
- keep the `docker-pom.xml` bootstrap step, but measure whether it should remain in the main production build path
- avoid invalidating the runtime stage when only source files change

## JVM tuning for containers

Java 21 is already container-aware, so the strategy should document **safe defaults**, not legacy flags.

Recommended production guidance:

- keep `-XX:+UseG1GC` unless benchmarking proves another collector is better
- add `-XX:+ExitOnOutOfMemoryError`
- prefer percentage-based memory settings over fixed heap assumptions, for example:
  - `-XX:InitialRAMPercentage=25`
  - `-XX:MaxRAMPercentage=70`
- keep application-specific options in `OMRS_JAVA_SERVER_OPTS`
- keep memory sizing in `OMRS_JAVA_MEMORY_OPTS`

Review the current default:

- `OMRS_JAVA_MEMORY_OPTS=-XX:NewSize=128m`

That value is not enough on its own for container sizing. Replace it with container-aware heap guidance for ACA workloads.

## Security hardening

Preserve and extend what already exists:

- keep `USER 1001`
- keep `tini`
- pin image versions and preferably digests
- generate an SBOM during CI
- scan base and application layers on every build
- keep writable paths minimal

If ACA deployment permits it without breaking startup, prefer:

- read-only root filesystem
- explicit writable mounts only for Tomcat temp/work and required OpenMRS data paths

## Multi-architecture support

Maintain support for:

- `linux/amd64`
- `linux/arm64`

This is already aligned with the existing Bamboo `buildx` usage and should be preserved in GitHub Actions.

---

## 4. Docker Compose updates

Compose should remain a **developer and CI convenience**, not a production deployment artifact.

## Recommended Compose structure

### `docker-compose.yml`

Base local development stack:

- OpenMRS app
- MariaDB
- named volumes for local persistence

This remains intentionally MariaDB-based for local developer convenience. Azure deployments use managed PostgreSQL instead.

### `docker-compose.override.yml`

Developer-only conveniences such as:

- port mappings
- debug port exposure
- local source mounts if retained

### `docker-compose.test.yml`

New file for integration and smoke tests in CI:

- app container
- database container
- ephemeral volumes only
- deterministic env values from CI secrets/variables
- no dev-only mounts

This file should be the basis for container-level integration checks in GitHub Actions.

## Remove hardcoded credentials from defaults

Current Compose defaults use values such as `openmrs` and `Admin123`. That is acceptable for quick local startup, but it should not be the documented long-term pattern.

Recommended update:

- add `.env.example`
- load local secrets from `.env`
- keep `.env` ignored by Git
- reserve inline defaults only for clearly non-sensitive local developer convenience

At minimum, remove or de-emphasize committed defaults for:

- database passwords
- root database passwords
- admin UI passwords
- Grafana admin password

## Use Compose profiles for optional services

Optional local services are a good fit for Compose profiles:

- `elasticsearch`
- `grafana`

That would make local startup clearer than stacking multiple files manually, while still preserving the existing optional-service model.

---

## 5. CI/CD pipeline for container builds

## Current state

Today, Bamboo already performs useful Docker work:

- Buildx-based multi-platform builds
- Docker image publishing
- separate dev and production targets

That should be treated as the baseline capability to preserve during migration.

## Target state: GitHub Actions + ACR

The target pipeline should:

1. build the application artifact
2. build the production container image
3. run image vulnerability scanning
4. generate SBOM/attestation metadata
5. push to ACR
6. deploy by digest to ACA

## Recommended GitHub Actions workflow stages

### 1. Validate

- run unit tests
- optionally run integration tests
- verify the WAR is produced successfully

### 2. Build image

- use `docker/setup-buildx-action`
- build multi-platform images for `linux/amd64,linux/arm64`
- use GitHub Actions cache and/or registry cache for BuildKit layers

### 3. Scan

- scan the built image before promotion
- surface critical/high vulnerabilities as policy gates
- rely on ACR + Defender as a second layer of scanning, not the only one

### 4. Push

- authenticate to ACR using OIDC/federated identity where possible
- push immutable tags and capture the resulting digest

### 5. Promote and deploy

- deploy ACA revisions using the pushed digest
- keep environment-specific configuration out of the image
- allow rollback by redeploying a previous digest

## Example tagging and promotion flow

For a release `3.0.0` at commit `abcdef1`:

- push `3.0.0`
- push `3.0.0-abcdef1`
- deploy `acr.example.io/openmrs-core@sha256:...`

For a non-release branch build:

- push `main-abcdef1`
- deploy only to non-production environments

---

## 6. Work items to split into GitHub issues

Each of the following should be tracked as a separate GitHub issue:

1. **Create production-focused Dockerfile strategy**
   - split dev and prod Dockerfiles, or simplify the existing multi-stage file

2. **Optimize production runtime image**
   - move toward JRE-only/slim runtime and reduce packages

3. **Harden runtime container**
   - digest pinning, SBOM, scan gates, writable path review

4. **Define ACA configuration contract**
   - env vars, secret refs, probe settings, managed identity requirements

5. **Define persistent data strategy for `/openmrs/data`**
   - image vs volume vs external service decision

6. **Refactor Docker Compose for dev/test**
   - add `.env.example`, remove hardcoded secrets, add CI test compose file

7. **Create GitHub Actions container pipeline**
   - build, scan, push to ACR, deploy by digest

8. **Add readiness endpoint or equivalent rollout-safe probe strategy**
   - separate liveness from readiness for ACA

9. **Document registry and tagging conventions**
   - semver, git SHA, branch tags, digest deployment

---

## Recommended implementation order

1. lock down the **production container contract**
2. define the **persistent data and secret strategy**
3. simplify and harden the **runtime image**
4. update Compose for **dev and CI**
5. migrate build/publish to **GitHub Actions + ACR**
6. connect container delivery into [03. Azure Infrastructure](03-azure-infrastructure.md)
7. schedule rollout work in [05. Migration Phases](05-migration-phases.md)

---

## Decisions recorded by this document

- OpenMRS Core is **already container-capable** and does not need a greenfield container rewrite.
- The current Docker setup should be **evolved**, not discarded.
- ACA deployment should use a **single immutable Tomcat-based runtime image**.
- Configuration must be **environment-driven and secret-backed**, not baked into the image.
- ACR is the target registry, and GitHub Actions is the target CI/CD system.
- Production deployment should be by **image digest**.
- Runtime mutability under `/openmrs/data` must be explicitly designed for ACA before production rollout.

## Remaining gaps

This document intentionally leaves these decisions for follow-up work:

- exact Azure-managed PostgreSQL sizing and connectivity details
- exact persistent storage implementation for mutable OpenMRS data
- whether Elasticsearch remains optional or becomes environment-specific
- whether distroless is viable after startup orchestration is simplified
- final ACA probe timings and scale rules

Those should be resolved in the linked Azure infrastructure and migration planning documents.

---

## Tracked Issues

### Phase 2 — Containerisation and Dev Environment

- [ ] #14 — Switch production Docker image to JRE-only base
- [ ] #25 — Add JVM container tuning flags (MaxRAMPercentage, G1GC)
- [ ] #32 — Optimise Dockerfile layer caching and build performance
- [ ] #43 — Add OCI standard labels and image metadata
- [ ] #47 — Review and update .dockerignore
- [ ] #65 — Configure container image vulnerability scanning
- [ ] #69 — Create GitHub Actions container build workflow (build → test → scan → push)
- [ ] #73 — Set up multi-platform builds with Docker Buildx (amd64 + arm64)
- [ ] #94 — Create .env.example for docker-compose secrets management
- [ ] #97 — Create docker-compose.test.yml for CI integration testing
- [ ] #98 — Document image tagging and promotion strategy
- [ ] #101 — Add VS Code Dev Container configuration
