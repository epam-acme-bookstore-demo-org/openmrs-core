---
name: azure-approve-splr
description: Shared private link resource discovery, approval, and verification workflow
license: MIT
---

# Azure Approve SPLR

Use this skill to locate and approve shared private link resources.

## Workflow (ordered)

- Discover pending shared private link requests.
- Verify target resources and intended connectivity.
- Apply approvals with explicit scope confirmation.
- Re-check status and capture evidence.

## Prerequisites

- Azure CLI authenticated and scoped to the intended subscription.
- Permissions to approve private endpoint connections on target resources.

## Safety checks

- Confirm target environment before approval actions.
- Record resource IDs and approval evidence.
- Re-run status checks after propagation delay when needed.

## Completion criteria

- No relevant pending shared private link requests remain.
- Approval status is verifiable from command output.
