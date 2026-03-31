---
name: github-hosted-private-networking
description: Workflow for GitHub-hosted runners to access private cloud resources safely
license: MIT
---

# GitHub Hosted Private Networking

Use this skill for CI/CD connectivity to private cloud endpoints.

## Focus

- Runner-to-private-resource network path design.
- Identity and access prerequisites.
- Validation checks for connectivity and resolution.
- Operational troubleshooting for common failures.

## Procedure

1. Confirm enterprise/network prerequisites and intended environment.
2. Provision/identify private network settings and delegated subnet.
3. Bind runner groups to network configuration.
4. Validate runner scheduling and private endpoint reachability.

## Pitfalls to avoid

- Using incorrect network resource identifiers.
- Missing outbound egress requirements for runner provisioning.
- Runner label mismatches causing queued jobs.
