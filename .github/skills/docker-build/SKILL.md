---
name: docker-build
description: Container image build, publish, scanning, and deployment workflow using Docker Buildx, with Podman as a supported alternative
license: MIT
---

# Container Image Build

Use this skill to build container images for local and remote environments.

Default to Docker Buildx with BuildKit when Docker is available. If the user or organization has Docker Desktop licensing concerns, offer Podman as the alternative engine and explain the trade-offs.

## Prerequisites

- Docker: Docker Engine or Docker Desktop available, with Buildx/BuildKit enabled.
- Podman: Podman available locally; rootless mode is preferred when supported.
- Registry credentials available for push workflows.
- For multi-platform builds, ensure the selected engine supports the target platforms.

## Focus

- Single-platform and multi-platform build paths.
- Registry tagging and publish strategy.
- Reproducibility, cache, and verification checks.
- Engine choice: Docker Buildx first, Podman when Docker licensing or daemon requirements are a problem.

## Engine selection

- Prefer Docker Buildx for the richest BuildKit workflow, especially when you need `--platform`, `--cache-from`, `--cache-to`, `--secret`, `--ssh`, `--sbom`, or `--provenance`.
- Offer Podman when the user prefers a daemonless engine, rootless operation, or wants to avoid Docker Desktop licensing constraints.
- Be explicit that Podman supports `podman buildx build` as a compatibility alias, but not every Docker Buildx feature is available. Prefer documenting Podman with `podman build` and `podman run`.

## Recommended Docker workflow

Use `docker buildx build` as the default build command.

### Local single-platform validation

```bash
docker buildx build \
	--progress=plain \
	--load \
	-t my-app:dev \
	.
```

Use this path for fast local validation when you want the image loaded into the local Docker image store.

### Multi-platform build and push

```bash
docker buildx build \
	--platform linux/amd64,linux/arm64 \
	--tag registry.example.com/my-app:1.2.3 \
	--tag registry.example.com/my-app:latest \
	--push \
	.
```

Use `--push` for multi-platform outputs when the local image store cannot load the final manifest list.

### Recommended BuildKit features

- Use `--cache-from` and `--cache-to` for CI and remote builders.
- Use `--secret` and `RUN --mount=type=secret` instead of baking credentials into images.
- Use `--ssh` for private source access during builds.
- Use `--sbom` and `--provenance` when your supply-chain workflow needs attestations.
- Use `.dockerignore` aggressively to reduce build context transfer and keep remote builds fast.
- Prefer multi-stage builds, slim base images, and remote fetch during build when appropriate.

Example with cache, SBOM, and provenance:

```bash
docker buildx build \
	--platform linux/amd64,linux/arm64 \
	--cache-from type=registry,ref=registry.example.com/my-app:buildcache \
	--cache-to type=registry,ref=registry.example.com/my-app:buildcache,mode=max \
	--sbom \
	--provenance=mode=max \
	--push \
	-t registry.example.com/my-app:1.2.3 \
	.
```

## Podman workflow

Use Podman when Docker licensing terms, Docker Desktop adoption, or daemon requirements are blockers.

### Local single-platform build

```bash
podman build \
	--format oci \
	-t localhost/my-app:dev \
	.
```

### Run the built image locally

```bash
podman run --rm -p 8080:8080 localhost/my-app:dev
```

### Multi-platform build

Podman supports multi-platform builds, but the workflow differs from Docker Buildx. Use `--platform` and `--manifest` when building multi-architecture images.

```bash
podman build \
	--platform linux/amd64,linux/arm64 \
	--manifest localhost/my-app:multi \
	.
```

If the build includes `RUN` instructions for non-native architectures, ensure emulation support is configured first.

### Podman-specific notes

- Prefer `podman build` over `podman buildx build`; the latter exists mainly for scripting compatibility.
- Podman defaults align well with rootless operation.
- For writable bind mounts on SELinux systems, document `:Z` or `:z` as needed.
- For registry auth, `podman login` uses the Podman auth file and can fall back to Docker credentials.
- Podman supports cache, secrets, and SBOM workflows too, but the flags and behavior are not identical to Docker Buildx.

## Procedure

1. Choose the engine: Docker Buildx by default, Podman when licensing or daemon concerns apply.
2. Start with a local single-platform build for fast validation.
3. Use explicit tags tied to a commit, version, or release.
4. Build for target platforms only when deployment compatibility requires it.
5. Use cache, secrets, and multi-stage builds deliberately; do not hide them behind implicit magic.
6. Push immutable tags and avoid relying on `latest` alone in release workflows.
7. Verify the resulting digest, supported platforms, and runtime pull success.

## Validation checklist

- The chosen engine is explicit and justified.
- Target platform set matches runtime requirements.
- Tagging strategy is traceable to commit or release.
- `.dockerignore` or `.containerignore` is present and sensible.
- Secrets are passed with build secret mechanisms, not `ARG` or `ENV`.
- Build and push logs are captured for troubleshooting.
- Image digest and pull/run behavior are verified after publication.

## CI/CD pipeline patterns

- Structure the pipeline as: build artifact → build image → scan → push → deploy
- Build the application artifact (JAR, binary, bundle) in a CI step before the Docker build — or use multi-stage Dockerfile
- Tag images with both the Git SHA and semantic version for traceability
- Use build cache (registry-backed `--cache-from`/`--cache-to`) to speed up CI builds
- Push to a staging registry or tag first; promote to production tag after validation
- Never use `latest` as the sole tag in deployment manifests — always pin a specific version
- Run smoke tests against the built image before pushing to production registry

## Image scanning

- Scan images for vulnerabilities before pushing to the registry
- Use tools like Trivy, Grype, or Snyk Container — integrate into CI as a gate
- Define severity thresholds: block on Critical/High, warn on Medium
- Scan both the base image and the application layers
- Re-scan images periodically — base image CVEs can appear after initial build
- Use `--exit-code 1` (Trivy) or equivalent to fail the pipeline on threshold breach
- Include SBOM generation (`--sbom`) for supply chain visibility

## Deployment strategies

- Rolling update (default): progressively replace old containers — set `maxSurge` and `maxUnavailable` for zero-downtime
- Blue-green: run old and new versions simultaneously, switch traffic atomically — use for critical releases
- Canary: route a percentage of traffic to the new version, monitor metrics, then promote or rollback
- Use health checks (liveness and readiness probes) to gate deployment progression
- Automate rollback on failed health checks or error rate spikes
- Log the deployed image digest for audit and incident correlation