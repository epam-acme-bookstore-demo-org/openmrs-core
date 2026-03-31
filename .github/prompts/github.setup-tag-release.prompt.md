---
description: "Generate a tag-based CD workflow with staged validation, build, publish, and release jobs"
argument-hint: "rag,mcp for packages · npm.pkg.github.com for registry · yes or no for docker"
---

# Setup Tag-Based Release Workflow

Generate a tag-based CD workflow for the following project.

## Project Parameters

**Packages (comma-separated directories):** ${input:packages}

**Registry URL:** ${input:registry}

**Build and push Docker image:** ${input:docker}

## Instructions

1. **Read the project structure** — inspect `package.json` (or equivalent manifest) files in each listed package directory to confirm the package names and current versions.
2. **Read `.github/workflows/`** — check for an existing CD workflow to avoid duplication; note any reusable workflow patterns already in use.
3. **Generate `.github/workflows/cd.yml`** with the following staged structure:

   ### `validate` job
   - Trigger: `on: push: tags: ['v*']`
   - Steps: checkout, setup runtime, install dependencies, run lint, run typecheck (if applicable), run full test suite.
   - Jobs fail on any non-zero exit code by default — no additional configuration needed.

   ### `build` job (`needs: validate`)
   - Steps: checkout, setup runtime, install dependencies, build each package listed in `${input:packages}`.
   - If `${input:docker}` is `yes`: build the Docker image and tag it with the git tag (`${{ github.ref_name }}`).
   - Upload build artifacts using `actions/upload-artifact`.

   ### `publish` job (`needs: build`)
   - Download artifacts from the build job.
   - Authenticate to `${input:registry}`.
   - Publish each package. For npm: `npm publish --access public`.
   - If `${input:docker}` is `yes`: push the tagged Docker image to the registry.
   - Pass the tag name via `env: TAG: ${{ github.ref_name }}` — never interpolate directly into shell scripts.

   ### `release` job (`needs: publish`)
   - Create the GitHub Release using `softprops/action-gh-release@v2` with `generate_release_notes: true`.
   - Attach any checksums or notable artifact metadata to the release body.

4. **Generate `.github/release.yml`** with PR-based release note categories (Breaking Changes, New Features, Bug Fixes, Maintenance).
5. **Add a version-sync step** to the `validate` job that reads the version from each package manifest and compares it to `github.ref_name`. Fail the job if any version does not match the tag (strip leading `v` before comparing).
6. **Validate the generated YAML** is syntactically correct before presenting it.
7. **Summarize required secrets** — list every secret (e.g. `NPM_TOKEN`, `DOCKER_PASSWORD`) that must be configured in the repository settings before the workflow can run.
8. **Remind the user** to:
   - Bump all package versions and commit to `main` before pushing the tag.
   - Push the tag with `git tag vX.Y.Z && git push origin vX.Y.Z`.
   - Verify the workflow run in the Actions tab after the first tag push.

## Reference

Follow the tag-based release patterns documented in the **release-process** skill and the **tag-based-release** instruction. Use the version-sync reference script from the **version-sync** skill for the manifest-to-tag comparison step.
