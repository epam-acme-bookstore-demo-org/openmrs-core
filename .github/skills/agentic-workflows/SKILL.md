---
name: agentic-workflows
description: Create and maintain GitHub Agentic Workflows — Markdown-based AI automations that run inside GitHub Actions with strong guardrails
license: MIT
---

# GitHub Agentic Workflows

Use this skill when creating, updating, debugging, or maintaining GitHub Agentic Workflows — Markdown-based repository automations powered by AI coding agents running inside GitHub Actions.

## When to Use

- Creating a new agentic workflow for repository automation.
- Updating an existing workflow's triggers, permissions, safe-outputs, or instructions.
- Debugging a workflow that fails to compile or behaves unexpectedly.
- Reviewing agentic workflow files for security and correctness.
- Setting up the `gh aw` toolchain for the first time.

---

## Workflow Anatomy

Every agentic workflow is a `.md` file in `.github/workflows/` with two parts:

1. **YAML Frontmatter** — triggers, permissions, tools, safe-outputs
2. **Markdown Body** — natural language instructions for the AI agent

A compiled `.lock.yml` file is generated alongside and must be committed together.

```
.github/workflows/
├── my-workflow.md          # Source (human-authored)
└── my-workflow.lock.yml    # Compiled (machine-generated)
```

---

## Frontmatter Quick Reference

```yaml
---
description: Brief purpose statement
on:
  schedule: daily                # daily | weekly | cron expression
  workflow_dispatch:             # Manual trigger
  slash_command:                 # ChatOps trigger
    name: command-name
  reaction: "eyes"              # Emoji on trigger event

permissions:
  contents: read
  issues: read
  pull-requests: read

safe-outputs:
  create-issue:
    title-prefix: "[Bot] "
    labels: [automated]
    expires: 2d
  create-pull-request:
    title-prefix: "[Bot] "
    labels: [automated]
    draft: true
    reviewers: [copilot]
  add-comment:
    target: "*"

tools:
  github:
    toolsets: [default]
  edit:
  bash: true
  web-fetch:
  repo-memory: true

timeout-minutes: 20
---
```

## Safe Output Types

| Output | Purpose |
|---|---|
| `create-issue` | Open a new issue with findings |
| `create-pull-request` | Open a PR with code changes |
| `add-comment` | Comment on existing issues/PRs |
| `update-issue` | Modify an existing issue |
| `push-to-pull-request-branch` | Push commits to an existing PR branch |

---

## Workflow Patterns

| Pattern | Trigger | Use Case |
|---------|---------|----------|
| **DailyOps** | `schedule: daily` | Recurring maintenance — doc updates, test improvements, reports |
| **WeeklyOps** | `schedule: weekly` or cron | Periodic audits — CLI consistency, dependency checks |
| **IssueOps** | `issues.opened` / `issues.labeled` | Auto-triage, labeling, routing |
| **ChatOps** | `slash_command` | On-demand tasks — `/test-assist`, `/review`, `/triage` |
| **ProjectOps** | Project board events | Cross-repo coordination and status updates |

---

## CLI Commands

### Setup

```bash
# Install the gh-aw extension
gh extension install github/gh-aw

# Verify installation
gh aw --version
```

### Compile

```bash
# Compile all workflows in .github/workflows/
gh aw compile

# Compile a specific workflow
gh aw compile .github/workflows/my-workflow.md

# Dry run — validate without writing .lock.yml
gh aw compile --dry-run
```

### Validate

```bash
# Check workflow syntax without compiling
gh aw validate .github/workflows/my-workflow.md
```

---

## Authoring Rules

1. **Read-only by default** — Only grant write access through `safe-outputs`, never through `permissions`.
2. **One goal per workflow** — Keep each workflow focused on a single automation objective.
3. **Be specific about outputs** — Define `title-prefix`, `labels`, and `expires` for every safe-output.
4. **Require evidence** — Instruct the agent to back findings with real data (logs, CLI output, diffs).
5. **Add exit conditions** — Define when the workflow should do nothing and exit gracefully.
6. **Use structured output** — Define the report/comment format explicitly in the Markdown body.
7. **Commit both files** — Both `.md` and `.lock.yml` must be committed together.

---

## Example: Daily Documentation Updater

```markdown
---
description: Detect stale documentation and open PRs with fixes
on:
  schedule: daily
  workflow_dispatch:
permissions:
  contents: read
  pull-requests: read
safe-outputs:
  create-pull-request:
    title-prefix: "[Docs] "
    labels: [documentation, automated]
    draft: true
    reviewers: [copilot]
tools:
  github:
    toolsets: [default]
  bash: true
  edit:
timeout-minutes: 15
---

You are a documentation specialist. Your job is to keep repository
documentation in sync with the actual codebase.

## Steps

1. Read the README and all docs/ files.
2. Compare documented setup steps against package.json scripts and config files.
3. Check that API docs match actual endpoints.
4. If discrepancies are found, create a PR fixing them.
5. If everything is in sync, exit without creating any output.
```

## Example: ChatOps Test Assistant

```markdown
---
description: Generate tests on demand via /test-assist command
on:
  slash_command:
    name: test-assist
  reaction: "eyes"
permissions:
  contents: read
  issues: read
  pull-requests: read
safe-outputs:
  create-pull-request:
    title-prefix: "[Tests] "
    labels: [testing, automated]
    draft: true
  add-comment:
    target: "*"
tools:
  github:
    toolsets: [default]
  bash: true
  edit:
timeout-minutes: 20
---

You are a test engineering specialist. When triggered by /test-assist,
analyze the referenced files or modules and generate targeted tests.

## Steps

1. Parse the triggering comment for file paths or module names.
2. Analyze the referenced code for untested paths.
3. Generate tests using the project's existing test framework.
4. Open a draft PR with the new tests.
5. Comment back on the issue with a summary of what was covered.
```

---

## Reference Implementations

These workflows from `microsoft/apm` serve as production-quality examples:

- [CLI Consistency Checker](https://github.com/microsoft/apm/blob/main/.github/workflows/cli-consistency-checker.md) — Weekly audit of CLI commands for naming and help-text consistency.
- [Daily Documentation Updater](https://github.com/microsoft/apm/blob/main/.github/workflows/daily-doc-updater.md) — Daily scan for stale docs, opens PRs with fixes.
- [Daily Test Improver](https://github.com/microsoft/apm/blob/main/.github/workflows/daily-test-improver.md) — Daily test coverage analysis + `/test-assist` slash command.

## Available Instructions and Prompts

- **Instruction**: `agentic-workflows` — Detailed frontmatter reference and authoring rules (applied to `.github/workflows/*.md` files).
- **Prompt**: `github.agentic-workflows.create` — Generate a complete agentic workflow for a given automation goal and target repository.

## Documentation

- [Agentic Workflows Technical Preview](https://github.blog/changelog/2026-02-13-github-agentic-workflows-are-now-in-technical-preview/)
- [gh-aw CLI Documentation](https://github.github.com/gh-aw/)
- [gh-aw Source Repository](https://github.com/github/gh-aw)
