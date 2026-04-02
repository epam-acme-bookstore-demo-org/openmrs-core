# OpenMRS Core Azure Infrastructure (Bicep)

This directory contains Infrastructure-as-Code for deploying OpenMRS Core on Azure Container Apps for:

- **dev**: low-cost, public ingress, no VNET complexity
- **prod**: VNET-isolated, Application Gateway (**WAF_v2**) as public ingress

## Architecture Overview

### Dev
- Azure Container Registry (Basic)
- Log Analytics workspace
- Azure Container Apps Environment (Consumption)
- Azure Container App (public ingress)
- Azure Database for MySQL Flexible Server (public access)

### Prod
- Everything in dev, plus:
- VNET `10.0.0.0/16` with subnets:
  - `aca-subnet` `10.0.0.0/23`
  - `appgw-subnet` `10.0.2.0/24`
  - `db-subnet` `10.0.3.0/24`
  - `mgmt-subnet` `10.0.4.0/24`
- NSGs applied per subnet
- Private endpoints for ACR and database
- Application Gateway WAF_v2 as ingress
- ACA internal ingress only

## Prerequisites

- Azure CLI `2.57+`
- Bicep CLI (`az bicep install` or `az bicep upgrade`)
- Access to target subscription with at least:
  - **Contributor** on target resource group
  - **User Access Administrator** (or Owner) for role assignments (AcrPull)
  - Networking permissions for VNET, NSG, private endpoints, and Application Gateway
- A database admin password provided at deploy time via environment variable:
  - `OPENMRS_DB_ADMIN_PASSWORD`

## Layout

```text
infrastructure/
  modules/
  environments/
    dev/
    prod/
  deploy.sh
```

## Deploy

From repository root:

```bash
chmod +x infrastructure/deploy.sh
export OPENMRS_DB_ADMIN_PASSWORD='REPLACE_ME'
./infrastructure/deploy.sh --env dev --resource-group rg-openmrs-dev --location eastus
./infrastructure/deploy.sh --env prod --resource-group rg-openmrs-prod --location eastus
```

The script performs:
1. `az deployment group what-if` (preview)
2. explicit confirmation prompt
3. `az deployment group create` (apply)

## Notes

- Secrets are not hardcoded in templates.
- Container App uses **system-assigned managed identity**.
- ACR image pull uses `AcrPull` role assignment to the Container App identity.
- Templates use **Azure Database for PostgreSQL Flexible Server** as the managed database service.
