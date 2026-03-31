---
description: Guidance for using Jira via the Atlassian MCP server
applyTo: "**"
---

# Jira MCP Integration

When Atlassian MCP tools are available, use them for:
- Creating and updating Jira issues (Epics, Stories, Tasks, Bugs)
- Reading issue details, acceptance criteria, and linked issues
- Searching issues with JQL queries
- Managing issue transitions and status updates
- Reading project metadata (components, versions, priorities)

## Issue Structuring Conventions

- **Epics** group related work under a business capability or feature area.
- **Stories** describe user-facing behavior with clear acceptance criteria.
- **Tasks** describe technical work that does not directly map to user behavior.
- **Bugs** describe defects with reproduction steps, expected vs. actual behavior.

## Acceptance Criteria Standards

- Write acceptance criteria as testable Given/When/Then statements or checkbox items.
- Each criterion must be independently verifiable.
- Cover happy paths, edge cases, and error scenarios.
- Link acceptance criteria to test cases when available.

## Workflow

1. Read existing project context (components, epics, priorities) before creating issues.
2. Check for duplicates before creating new issues.
3. Link related issues (blocks, is-blocked-by, relates-to).
4. Set appropriate priority, labels, and component fields.

Prefer MCP tool calls over REST API calls when both are available.