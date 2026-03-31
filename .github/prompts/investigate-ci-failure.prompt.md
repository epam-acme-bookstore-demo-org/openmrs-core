---
description: Analyze a CI/CD pipeline failure, identify root cause, and suggest remediation
argument-hint: Provide the URL of the failed workflow run and any additional context (e.g. recent changes, error messages, suspected areas).
---

# Investigate CI Failure

A CI/CD pipeline has failed and needs investigation.

## Failure Reference

${input:workflow_url}

## Additional Context

${input:additional_context}

## Instructions

1. Retrieve the workflow run details and job logs using GitHub MCP tools.
2. Identify the specific step and command that failed.
3. Retrieve the PR diff or recent commits that triggered the run.
4. Correlate the failure with changed code paths.
5. Classify the failure type (regression, flaky test, infrastructure, dependency, configuration).
6. Provide a root cause analysis with evidence from logs and diffs.
7. Suggest a specific fix or next investigation steps.

Output your analysis using the CI Failure Analysis format defined in the CI Investigation standards.