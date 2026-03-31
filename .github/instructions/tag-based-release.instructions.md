---
description: "Release workflow detected — load release-process skill for tag-based atomic release patterns"
applyTo: "**/.github/workflows/cd*.yml"
---

# Tag-Based Release Patterns

When creating or modifying CD workflow files, follow the tag-based atomic release model from the **release-process** skill.

## Trigger

Prefer `on: push: tags: ['v*']` over `on: release: published`. The git tag is the single source of truth and ensures the pipeline runs only after the developer has deliberately signalled a release intent.

```yaml
on:
  push:
    tags: ['v*']
```

## Stage Ordering

Gate every downstream job with `needs:` to ensure all-or-nothing atomicity. A partial publish (some packages succeed, others fail) leaves the registry in an inconsistent state.

```yaml
jobs:
  validate:   # lint, typecheck, full test suite
  build:
    needs: validate
  publish:
    needs: build
  release:
    needs: publish
```

## Pre-publish Validation

Run the full test suite in the `validate` job **before** any artifact is built or pushed. Publishing must never proceed if tests fail.

## Version Consistency

Before building, verify that the version field in all manifests (`package.json`, `pyproject.toml`, `Cargo.toml`) matches the git tag. Use the **version-sync** skill for the validation pattern and reference script.

## GitHub Release Creation

Auto-create the GitHub Release in the final `release` job using `softprops/action-gh-release`. Do not create the release manually or before artifacts are verified.

```yaml
- uses: softprops/action-gh-release@v2
  with:
    generate_release_notes: true
```

Pair with `.github/release.yml` to categorize PR-based release notes automatically.

## Release Notes Configuration

Add `.github/release.yml` to the repository to control how pull requests are categorized in auto-generated release notes:

```yaml
changelog:
  categories:
    - title: Breaking Changes
      labels: [breaking-change]
    - title: New Features
      labels: [enhancement]
    - title: Bug Fixes
      labels: [bug]
    - title: Maintenance
      labels: [chore, dependencies]
```

## Security

Pass `github.ref_name` via an `env:` block — never interpolate it directly into shell commands with `${{ }}`. Interpolating user-controlled values into shell scripts allows command injection if a crafted tag name contains shell metacharacters.

```yaml
env:
  TAG: ${{ github.ref_name }}
run: echo "Releasing $TAG"  # safe — value is data, not code
```
