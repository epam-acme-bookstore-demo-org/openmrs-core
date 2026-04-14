# 09. Image Tagging Strategy

Back to [02. Containerisation Strategy](./02-containerisation.md)  
Reference workflow: [`.github/workflows/container-build.yml`](../../.github/workflows/container-build.yml)

## Purpose

Define the image-tagging and promotion contract for Phase 2 so CI builds, security scanning, and Azure deployments all use the same rules.

## Registry roles

- **GHCR** is the CI/CD registry. GitHub Actions builds there first and publishes traceable tags for merged and release builds.
- **ACR** is the Azure deployment registry. Azure Container Apps should pull only from ACR after an approved promotion step.

## Tag types

- `sha-<short>` — immutable commit tag for every pushed build.
- `master` — moving tag for the latest successful merge to `master`.
- `pr-<number>` — PR validation tag for build metadata only; PR images are **not pushed**.
- `v<major>.<minor>.<patch>` — immutable release tag from a Git tag such as `v2.4.1`.
- `v<major>.<minor>` — floating minor release tag, for example `v2.4`.

`latest` is forbidden. It must not be published, promoted, or referenced by deployment configuration.

## Promotion flow

1. **Pull request**: build only for validation; do not push an image.
2. **Merge to `master`**: push `sha-<short>` and `master` to GHCR.
3. **Git tag `v*`**: push `sha-<short>`, `vX.Y.Z`, and `vX.Y` to GHCR.
4. **Promote to ACR**: copy the approved image from GHCR to ACR by digest using `az acr import` or an equivalent CI job.
5. **Deploy to ACA**: reference the ACR image by **SHA digest**, not by tag.

## Scanning and deployment rules

- Run **Trivy** in CI against the built image.
- Upload results as **SARIF** to the GitHub Security tab.
- Treat digest pinning as mandatory for every Azure deployment.
- Use tags for discovery and release management only; use digests for rollout safety.

## Environment promotion usage

- **PR validation** stays build-only and does not produce a deployable registry artifact.
- **Dev and test deployments** should promote a specific GHCR digest into ACR and deploy that promoted digest.
- **Production deployments** must reuse an already-approved ACR digest rather than rebuilding or retagging from source.

## Retention and cleanup

- Keep release tags (`vX.Y.Z`, `vX.Y`) for auditability and rollback.
- Treat `sha-<short>` as the immutable traceability tag for each published build.
- Allow mutable tags such as `master` to move forward, but do not use them for deployment history.
- Apply registry cleanup to unneeded superseded non-release artifacts according to GHCR and ACR retention policy, while preserving digests referenced by deployed environments.

## Common scenarios

- **PR #123**: build `pr-123` for validation only; do not push it.
- **Merge to `master` at commit `abc1234`**: push `sha-abc1234` and `master` to GHCR, then promote the approved digest to ACR for deployment.
- **Release `v2.4.1`**: push `sha-abc1234`, `v2.4.1`, and `v2.4` to GHCR, then promote that digest through the required Azure environments.
- **Hotfix release `v2.4.2`**: build from the hotfix commit, publish new immutable release tags, and promote the new digest without changing older release digests.

## Related documents

- [02. Containerisation Strategy](./02-containerisation.md)
- [`.github/workflows/container-build.yml`](../../.github/workflows/container-build.yml)
