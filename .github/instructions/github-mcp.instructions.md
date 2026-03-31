---
description: "GitHub MCP integration guidance for agent workflows"
applyTo: "**"
---

# GitHub MCP Integration

When GitHub MCP tools are available, use them for:
- Issue and PR discovery, creation, and updates
- Repository metadata and branch operations
- Actions workflow status and artifact retrieval
- Code search across the organization
- **GitHub Projects** — status transitions, item management, and field updates

Prefer MCP tool calls over CLI commands (`gh`) when both are available.

## GitHub Projects — Keep Status Updated

Whenever you **create, pick up, complete, or close** an issue or PR, check whether the repository uses a GitHub Project and update the item's status accordingly:

1. **Discover the project** — use `projects_list` (method: `list_projects`) for the repository owner to find active projects. Cache the project number for the session.
2. **Find the Status field** — use `projects_list` (method: `list_project_fields`) to locate the `Status` field and its allowed values (e.g. `Backlog`, `Todo`, `In Progress`, `In Review`, `Done`).
3. **Transition on key events**:
   - Issue created → add to project (`projects_write` method: `add_project_item`), set status to **Backlog** or **Todo**.
   - Issue picked up / work started → set status to **In Progress**.
   - PR opened or ready for review → set status to **In Review**.
   - PR merged / issue closed as completed → set status to **Done**.
4. **Match the project's vocabulary** — use the exact status values from the field definition; do not assume names.
5. If no project exists, skip silently — do not prompt the user to create one.
