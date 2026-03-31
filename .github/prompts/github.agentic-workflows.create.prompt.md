---
description: Generate a GitHub Agentic Workflow (.md) for a repository automation task
argument-hint: "Describe the automation goal and target repository (e.g. goal: \"triage new issues\", repo: \"owner/name\")"
---

# Create Agentic Workflow

Generate a GitHub Agentic Workflow for the following automation goal:

## Goal

${input:automation_goal}

## Target Repository

${input:target_repository}

## Instructions

1. **Analyze the target repository** to understand its structure, languages, frameworks, existing CI/CD workflows, and testing setup.
2. **Determine the appropriate trigger**:
   - `schedule: daily` or cron for recurring maintenance
   - `workflow_dispatch` for manual runs
   - `slash_command` for on-demand ChatOps
   - Event triggers (`issues.opened`, `pull_request.opened`) for reactive workflows
3. **Define minimal permissions** — start with `read` only; never grant `write` directly.
4. **Select safe-outputs** — choose only the write operations needed (create-issue, create-pull-request, add-comment).
5. **Choose tools** — `github` for MCP access, `bash: true` for shell commands, `edit` for file changes, `web-fetch` for HTTP.
6. **Write clear Markdown instructions** that:
   - Define the agent's role and expertise area
   - Break the task into numbered steps
   - Specify evidence requirements (real CLI output, not fabricated)
   - Define the output format (report structure, PR description template)
   - Include graceful exit conditions (e.g., "if no changes found, exit without creating output")
7. **Create the workflow file** at `.github/workflows/<name>.md` following the frontmatter + Markdown body structure.
8. **Remind the user** to:
   - Run `gh aw compile` to generate the `.lock.yml`
   - Configure any required secrets (coding agent API keys)
   - Test with `workflow_dispatch` before enabling schedules
   - Commit both `.md` and `.lock.yml` files

## Reference

Use the GitHub Agentic Workflows documentation at https://github.github.com/gh-aw/ and the workflow creation guide at https://raw.githubusercontent.com/github/gh-aw/main/create.md for syntax and best practices.
