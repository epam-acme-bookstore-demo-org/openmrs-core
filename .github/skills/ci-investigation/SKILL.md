---
name: ci-investigation
description: CI/CD failure investigation patterns — workflow for isolating failures, classifying root causes, and proposing fixes
license: MIT
---

# CI Failure Investigation

Use this skill when investigating CI/CD pipeline failures or diagnosing build/test regressions.

## Investigation Workflow

1. **Gather context** — Retrieve the failed workflow run, job logs, and triggering event (PR, push, schedule).
2. **Isolate the failure** — Identify the specific step, test, or command that failed.
3. **Correlate with changes** — Compare the failure against the PR diff or recent commits to find the likely cause.
4. **Classify the failure** — Categorize as: code regression, flaky test, infrastructure issue, dependency change, or configuration drift.
5. **Propose remediation** — Suggest a specific fix with code changes, or flag for human investigation if unclear.

## Failure Classification

| Category | Signals | Typical Fix |
|----------|---------|-------------|
| Code regression | Test failure matches changed code paths | Fix the code change |
| Flaky test | Test passes on re-run, non-deterministic assertions | Stabilize the test |
| Infrastructure | Timeout, OOM, network errors, runner issues | Retry or adjust resources |
| Dependency | Version mismatch, breaking upstream change | Pin or update dependency |
| Configuration | Missing env var, secret, or permission | Fix pipeline config |

## Correlation Rules

- Map failed test files to changed files using import graphs or naming conventions.
- Check if the failure existed before the PR branch (compare with base branch CI).
- Look for patterns: same test failing across multiple PRs suggests flaky test, not regression.

## Output Format

```markdown
**Failed Step**: <workflow > job > step>
**Error Summary**: <one-line description>
**Category**: <regression | flaky | infrastructure | dependency | configuration>
**Root Cause**: <detailed explanation>
**Evidence**: <relevant log lines, diff hunks, or test output>
**Suggested Fix**: <specific remediation steps or code change>
**Confidence**: <high | medium | low>
```