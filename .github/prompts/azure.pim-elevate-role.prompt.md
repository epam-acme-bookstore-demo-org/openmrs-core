---
description: Elevate an Azure PIM eligible role assignment to active using Azure CLI and the PIM REST API
argument-hint: >
  Provide the target scope (full ARM path, e.g. /subscriptions/<id> or /subscriptions/<id>/resourceGroups/<rg>),
  the role to activate (e.g. Owner, Contributor), and a justification for the activation.
  Optionally specify the activation duration (default: 1 hour).
---

# Azure PIM — Elevate Eligible Role

Activate an Azure Privileged Identity Management (PIM) eligible role assignment via the Azure CLI REST API.

## Inputs

- **Scope**: ${input:scope} — full ARM scope path, e.g. `/subscriptions/<id>` or `/subscriptions/<id>/resourceGroups/<rg>` (never a bare subscription GUID)
- **Role**: ${input:role_name} (e.g. Owner, Contributor, User Access Administrator)
- **Justification**: ${input:justification}
- **Duration**: ${input:duration} (default: PT1H — 1 hour)

## Instructions

### Step 1 — Resolve the current user's identity

Run `az ad signed-in-user show --query id -o tsv` to obtain the **principalId** (Object ID) of the currently authenticated user. Store this value for subsequent API calls.

### Step 2 — Discover eligible role assignments

Query the PIM REST API for eligible role assignments at the target scope:

```
GET https://management.azure.com/{scope}/providers/Microsoft.Authorization/roleEligibilityScheduleInstances?api-version=2020-10-01&$filter=assignedTo('{principalId}')
```

Use `az rest --method GET --url "<url>"` to execute.

From the response, extract:
- **roleDefinitionId** — the full resource ID of the role definition (e.g. `.../roleDefinitions/8e3af657-a8ff-443c-a75c-2fe8c4bcb635` for Owner)
- **roleEligibilityScheduleId** — the schedule ID to link the activation request to

Confirm the discovered role matches the requested role name before proceeding.

### Step 3 — Build the activation request body

Construct a JSON file (e.g. `/tmp/pim-activate.json`) with the following structure:

```json
{
  "properties": {
    "principalId": "<principalId from Step 1>",
    "roleDefinitionId": "<roleDefinitionId from Step 2>",
    "requestType": "SelfActivate",
    "linkedRoleEligibilityScheduleId": "<roleEligibilityScheduleId from Step 2>",
    "justification": "<justification from input>",
    "scheduleInfo": {
      "startDateTime": "<current UTC time in ISO 8601, e.g. 2026-01-15T10:00:00.000Z>",
      "expiration": {
        "type": "AfterDuration",
        "duration": "<duration from input, default PT1H>"
      }
    }
  }
}
```

**Implementation note**: Generate the JSON programmatically (e.g. using Python's `json` module) to avoid shell quoting issues with nested JSON. Generate a new UUID for the request identifier.

### Step 4 — Submit the activation request

```
PUT https://management.azure.com/{scope}/providers/Microsoft.Authorization/roleAssignmentScheduleRequests/{newGuid}?api-version=2020-10-01
```

Execute with `az rest --method PUT --url "<url>" --body @/tmp/pim-activate.json`.

**Handle known responses**:
- **Success (200/201)**: Role activation is in progress. Proceed to verification.
- `RoleAssignmentExists`: The role is already active — skip to verification.
- `AuthorizationFailed`: The user does not have an eligible assignment at this scope. Re-check the scope and eligibility.

### Step 5 — Verify the active role assignment

Confirm the role is now active:

```sh
az role assignment list --scope "<scope>" --assignee "<principalId>" --query "[].{role:roleDefinitionName, scope:scope}" -o table
```

The target role should appear in the output.

## Safety Checks

- **Never guess role definition IDs** — always discover them from the eligibility query.
- **Confirm the scope** — verify the subscription and resource group before activating.
- **Minimum duration** — request only the time needed (default 1 hour). PIM policies may enforce maximum limits.
- **Clean up** — the activation expires automatically. No manual deactivation is needed unless requested.

## Completion Criteria

- The eligible role assignment is activated and verifiable via `az role assignment list`.
- The activation duration and scope match the user's request.
- The justification is recorded in the PIM audit log.

## Reference

- [PIM Role Assignment Schedule Requests — REST API](https://learn.microsoft.com/en-us/rest/api/authorization/role-assignment-schedule-requests/create)
- [PIM Role Eligibility Schedule Instances — REST API](https://learn.microsoft.com/en-us/rest/api/authorization/role-eligibility-schedule-instances/list-for-scope)
