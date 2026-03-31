---
name: azure-standards
description: Azure development and infrastructure standards — SDK integration, authentication, security defaults, and testing
license: MIT
---

# Azure Development & Infrastructure Standards

Use this skill when implementing Azure SDK integrations, provisioning Azure infrastructure, or testing Azure resources.

## SDK integration

- Use official Azure SDK clients; prefer them over raw REST calls.
- Use `DefaultAzureCredential` for authentication — never hardcode keys or connection strings.
- Configure retry options on all SDK clients.
- Use connection pooling; register clients as singletons via DI.
- Handle transient failures with built-in SDK retry policies.
- Never log credentials or connection strings.

## Infrastructure

- Implement all Azure infrastructure changes using IaC (Pulumi, Terraform, or Bicep).
- Use managed identities over service principals where possible.
- Prefer private endpoints for PaaS services.
- Tag all resources with `environment`, `owner`, and `cost-center`.
- Use Azure Key Vault for secrets; never hardcode connection strings.
- Follow Azure naming conventions (`<resource-type>-<workload>-<environment>`).
- Preview changes before applying; flag destructive operations for human confirmation.
- Flag SKU changes that significantly impact cost.

## Security defaults

- Least-privilege access: grant only the permissions required for the workload.
- Encryption at rest and in transit for all data services.
- Prefer managed identities and workload identity over static secrets.
- Use private networking (private endpoints, VNet integration) where supported.
- Validate RBAC assignments and managed identity access.

## Testing Azure integrations

- Test with emulators where available (Azurite, CosmosDB emulator).
- Validate resource tags, SKUs, and network configuration after deployment.
- Test RBAC assignments and managed identity access.
- Verify private endpoint connectivity.
- Use Azure Resource Graph queries for infrastructure validation.
- Use non-destructive (read-only) checks for production resources.
