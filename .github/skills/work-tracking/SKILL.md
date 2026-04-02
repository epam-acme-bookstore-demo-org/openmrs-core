---
name: work-tracking
description: Platform-agnostic work item lifecycle contract with status transitions, agent mandates, and adapter patterns
license: MIT
---

# Work Item Lifecycle

Use this skill when agents pick up, execute, or complete work linked to a tracked work item (GitHub Issue, Jira ticket, ADO work item, etc.).

## Lifecycle States

Every work item follows this universal lifecycle, regardless of the tracking platform:

```
Backlog → In Progress → In Review → Done
                ↑              │
                └──────────────┘  (rework loop)
```

| State | Meaning |
|---|---|
| **Backlog** | Item created and prioritized but not yet started |
| **In Progress** | An agent or human is actively working on the item (includes rework after review rejection) |
| **In Review** | Work is complete and awaiting review (PR opened, QA pending) |
| **Done** | Work accepted and merged/deployed |

## Lifecycle Events

Agents must trigger status transitions at these points during execution:

| Event | From | To | Who triggers |
|---|---|---|---|
| Work picked up | Backlog | In Progress | Any specialist starting work |
| PR opened / review requested | In Progress | In Review | Developer, Infrastructure |
| Review approved and merged | In Review | Done | PR Reviewer, Tech Lead |
| Review rejected | In Review | In Progress | PR Reviewer, Quality gate |
| Work completed (no PR needed) | In Progress | Done | Any specialist |

## Agent Mandates

### When starting work on a task linked to a work item

1. Transition the work item status to **In Progress**.
2. Add a brief comment noting that work has started and which agent is working on it.

### When completing work

1. If a PR was created, transition to **In Review** and link the PR to the work item.
2. If no PR is needed (documentation-only, config change), transition directly to **Done**.
3. Add a comment summarizing what was accomplished.

### When work is rejected during quality review

1. Transition the work item back to **In Progress**.
2. Add a comment noting the rejection reason and what needs to change.

### When no work tracking tools are available

Skip all status updates silently. Do not prompt the user to set up work tracking.

## Platform Adapter Patterns

### GitHub Projects

**Detection**: GitHub MCP tools are available and the repository has an associated GitHub Project.

**Discovery**:
1. Use `projects_list` (method: `list_projects`) to find active projects for the repository owner.
2. Use `projects_list` (method: `list_project_fields`) to find the `Status` field and its allowed values.

**Transition**:
- Use `projects_write` (method: `update_project_item`) to set the Status field.
- Match the project's exact status vocabulary — do not assume field names.

**Work item reference format**: `#<issue-number>` (e.g. `#42`)

### Jira

**Detection**: Atlassian MCP tools are available.

**Discovery**:
1. Use Atlassian MCP to read the issue's current status and available transitions.

**Transition**:
- Use the Atlassian MCP `transition_issue` operation to move the issue through its workflow.
- Map lifecycle states to the project's actual Jira workflow statuses.

**Work item reference format**: `<PROJECT>-<number>` (e.g. `PROJ-123`)

### Azure DevOps

**Detection**: Azure DevOps MCP tools are available.

**Discovery**:
1. Query the work item's current state and available transitions.

**Transition**:
- Use the ADO API to update the work item state field.
- Map lifecycle states to the board's column definitions.

**Work item reference format**: `AB#<id>` (e.g. `AB#456`)

## Delegation Integration

When a tech-lead delegates work, the delegation contract should include the work item reference:

```markdown
**Goal**: <what to achieve>
**Work Item**: #NN (GitHub) | PROJ-123 (Jira) | AB#456 (ADO) | N/A
**Scope**: <files/modules in scope>
**Acceptance Criteria**: <what "done" looks like>
**Validation**: <commands to run>
**Context**: <relevant background or decisions>
```

The specialist receiving the delegation uses the **Work Item** field to identify which item to update during execution.
