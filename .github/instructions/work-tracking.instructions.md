---
description: >
  Always-on work item lifecycle awareness.
  Ensures agents update work item status during the delivery loop
  when work tracking tools are available.
applyTo: "**"
---

# Work Item Lifecycle Awareness

When you begin or complete work on a task that is linked to a tracked work item (GitHub Issue, Jira ticket, ADO work item), update its status:

1. **Starting work** → transition to **In Progress** and add a comment noting work has started.
2. **PR created or review requested** → transition to **In Review**.
3. **Work completed (no PR needed)** → transition to **Done** and add a summary comment.
4. **Review rejected / rework needed** → transition back to **In Progress** with rejection context.

## Platform Detection

- **GitHub Projects**: use `projects_write` via GitHub MCP to update the project item status.
- **Jira**: use `transition_issue` via Atlassian MCP to move the issue through its workflow.
- **Azure DevOps**: use the ADO MCP/API to update the work item state.

If no work tracking tools are available, skip silently.
