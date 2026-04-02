# Azure Container Apps infrastructure plan

This document defines the target Azure infrastructure for running OpenMRS Core on Azure Container Apps in two environments:

- **Development**: low-cost, quick to provision, publicly reachable for testing
- **Production**: private-by-default, VNET-isolated, with Application Gateway as the only ingress point

It builds on the application runtime and container assumptions described in [README.md](../../README.md), [02-containerisation.md](./02-containerisation.md), and [05-migration-phases.md](./05-migration-phases.md).

## Assumptions and deployment baseline

- OpenMRS Core runs as a **Java 21** application.
- The container image runs the application as a **WAR on Tomcat 11**.
- The runtime container exposes **port 8080** and runs as **non-root user 1001**.
- Health endpoint: **`/openmrs/health/alive`**
- Database engines supported by the application: **MariaDB, MySQL, PostgreSQL**
- Schema migrations are handled by **Liquibase**
- **Elasticsearch** is optional and should not block initial Azure adoption
- Local development today uses Docker Compose for app + database, with optional Elasticsearch and Grafana

> **Azure database note:** Azure does **not** offer a current “MariaDB Flexible Server” service. For Azure-native managed deployments, the recommended managed database is **Azure Database for MySQL Flexible Server**. If PostgreSQL becomes the preferred long-term managed option, the same network and secret patterns in this plan still apply.

---

## 1. Architecture overview

### 1.1 Development environment architecture

**Text diagram**

```text
Internet
  |
  v
Azure Container App (public ingress, consumption)
  |
  +--> Azure Key Vault
  +--> Azure Container Registry
  +--> Log Analytics
  |
  v
Azure Database for MySQL Flexible Server (public access, firewall restricted)

Optional:
  Azure-hosted Elasticsearch
```

> 📐 **Lucidchart Diagram**: [Dev Environment Architecture — _link TBD, created in Phase 0_]
> The text diagram above is a simplified summary. See the Lucidchart diagram for the authoritative, detailed view.

**Design intent**

- Minimise provisioning time and monthly cost
- Avoid VNET design and private networking complexity
- Allow short-lived environments and scale-to-zero behavior
- Keep secrets out of application configuration by storing them in Key Vault

**Development component inventory**

| Layer | Azure service | Recommended SKU / mode | Notes |
|---|---|---|---|
| Compute | Azure Container Apps Environment | Consumption | Shared environment for dev/test workloads |
| App | Azure Container App | 1 app, external ingress | OpenMRS runtime on port 8080 |
| Registry | Azure Container Registry | Basic | Stores OpenMRS images |
| Database | Azure Database for MySQL Flexible Server | Burstable B1ms/B1s or Basic-equivalent low tier | Use public access with tight firewall rules |
| Secrets | Azure Key Vault | Standard | Store DB credentials, OpenMRS admin/bootstrap secrets |
| Logging | Log Analytics workspace | Pay-as-you-go | Required by Container Apps |
| Search | Elasticsearch | Optional / skip | Skip in initial dev unless search validation is required |

### 1.2 Production environment architecture

**Text diagram**

```text
Internet / Corporate users
  |
  v
Public IP
  |
  v
Azure Application Gateway v2 (WAF enabled, TLS termination, HTTP->HTTPS redirect)
  |
  v
Private frontend/backend routing inside VNET
  |
  v
Azure Container Apps Environment (workload profiles, internal ingress only)
  |
  +--> Azure Key Vault (private endpoint)
  +--> Azure Container Registry (private endpoint)
  +--> Azure Monitor / Log Analytics / Application Insights
  +--> Optional Elasticsearch / Azure AI Search equivalent, private access
  |
  v
Azure Database for MySQL Flexible Server (private endpoint, HA, backups)
```

> 📐 **Lucidchart Diagram**: [Production Environment Architecture — _link TBD, created in Phase 0_]
> The text diagram above is a simplified summary. See the Lucidchart diagram for the authoritative, detailed view including subnet CIDRs, NSG rules, and private endpoint connections.

**Design intent**

- No public access to backend services
- Defense in depth via WAF, private endpoints, NSGs, managed identity, and policy
- Production-ready availability with at least two warm replicas
- Centralised monitoring, alerting, and compliance enforcement

**Production component inventory**

| Layer | Azure service | Recommended SKU / mode | Notes |
|---|---|---|---|
| Edge ingress | Application Gateway | **WAF v2** | Only public entry point |
| Networking | Azure VNET + subnets + NSGs + private DNS | Dedicated | Backend isolation and name resolution |
| Compute platform | Azure Container Apps Environment | **Workload profiles** | Production-grade ACA environment |
| App | Azure Container App | Internal ingress only | OpenMRS app on port 8080 |
| Registry | Azure Container Registry | Standard or Premium | Premium preferred if private link and enterprise controls are required |
| Database | Azure Database for MySQL Flexible Server | General Purpose or Business Critical | HA + geo-redundant backup |
| Secrets | Azure Key Vault | Standard/Premium with private endpoint | Access via managed identity |
| Monitoring | Azure Monitor + Log Analytics | Standard | Logs, metrics, alerting |
| APM | Application Insights | Optional | Java agent-based tracing/telemetry |
| Security | Defender for Containers, Azure Policy | Enabled | Baseline protection and governance |
| Search | Elasticsearch | Optional | Prefer private connectivity if enabled |

---

## 2. Development environment: simple and low cost

This environment is for developer validation, integration testing, demos, and pre-production smoke testing. It deliberately trades network isolation for simplicity.

### 2.1 Compute

- **Azure Container Apps Environment**
  - Plan: **Consumption**
  - Region: same region as database and ACR
- **OpenMRS Container App**
  - Ingress: **external enabled**
  - Target port: **8080**
  - Public FQDN: Azure-generated is acceptable for dev
  - Min replicas: **0**
  - Max replicas: **2**
  - Revision mode: **single** by default, switch to multiple only when testing release traffic splits
  - CPU/memory starting point: **1 vCPU / 2 GiB**
  - Health checks:
    - Startup/readiness path: `/openmrs/health/alive`
    - Liveness path: `/openmrs/health/alive`

### 2.2 Database

Recommended managed option:

- **Azure Database for MySQL Flexible Server**
  - Tier: **Burstable**
  - Starting size: **B1ms** or equivalent lowest practical size
  - Public access: **enabled**
  - Firewall rules:
    - Restrict to approved developer IP ranges where possible
    - Add Azure Container Apps outbound IPs if static/known for the environment
  - Backups: default retention acceptable for dev
  - HA: **not required** for dev

If strict MariaDB parity is needed for a specific test case, document that separately; the default Azure-native recommendation remains MySQL Flexible Server.

### 2.3 Supporting services

- **Azure Container Registry, Basic SKU**
  - Stores build artifacts
  - Enable admin user only if absolutely required for bootstrapping; prefer identity-based pull
- **Azure Key Vault**
  - Store:
    - database hostname
    - database name
    - database username
    - database password
    - OpenMRS admin/bootstrap secret values
  - Access policy or RBAC:
    - allow deployment pipeline to set secrets
    - allow Container App managed identity to read secrets
- **Log Analytics workspace**
  - Collect Container Apps logs and console output
  - Retention can stay low in dev to control cost

### 2.4 Optional services

- **Elasticsearch**
  - Optional for dev
  - Recommended default: **skip**
  - Add only when search-specific validation is in scope

### 2.5 Recommended dev resource group contents

```text
rg-openmrs-dev
  - Microsoft.App/managedEnvironments
  - Microsoft.App/containerApps
  - Microsoft.ContainerRegistry/registries
  - Microsoft.DBforMySQL/flexibleServers
  - Microsoft.KeyVault/vaults
  - Microsoft.OperationalInsights/workspaces
```

### 2.6 Dev provisioning principles

- One resource group per environment
- One Container Apps environment per lifecycle boundary
- Keep naming predictable, for example:
  - `acae-openmrs-dev`
  - `aca-openmrs-dev`
  - `acropenmrsdev`
  - `kv-openmrs-dev`
  - `mysql-openmrs-dev`

---

## 3. Production environment: fully isolated

Production should be deployed as a private-by-default platform with Application Gateway controlling all inbound traffic.

### 3.1 Networking topology

Recommended topology: **hub-spoke** if the organisation already operates a central hub; otherwise a **flat VNET** is acceptable for the first production release.

- **Preferred enterprise pattern**: hub-spoke
  - Hub:
    - shared DNS
    - firewall/NVA if mandated
    - shared ingress governance
  - Spoke:
    - OpenMRS production workload
    - isolated subnets
    - private endpoints

- **Pragmatic first release pattern**: flat VNET
  - Single VNET dedicated to OpenMRS production
  - Easier to provision and operate initially
  - Can later be peered into a hub

### 3.2 Suggested VNET and subnets

Example address space:

- **VNET**: `10.40.0.0/16`

Suggested subnets:

| Subnet | Suggested CIDR | Purpose | Notes |
|---|---|---|---|
| `snet-appgw` | `10.40.0.0/24` | Application Gateway subnet | Must be dedicated to Application Gateway |
| `snet-aca-infra` | `10.40.1.0/23` | Azure Container Apps environment infrastructure | Delegated for ACA environment requirements |
| `snet-private-endpoints` | `10.40.3.0/24` | Private endpoints for DB, Key Vault, ACR, monitoring | Centralises PaaS private endpoint NICs |
| `snet-management` | `10.40.4.0/24` | Optional admin/jump/private tooling | Use only if operationally required |

If a separate subnet is preferred for strict segregation, split `snet-private-endpoints` into:

- `snet-db-pe` – `10.40.3.0/25`
- `snet-sec-pe` – `10.40.3.128/25`

> 📐 **Lucidchart Diagram**: [Production Network Topology (VNET, Subnets, NSGs) — _link TBD, created in Phase 0_]
> The subnet table and NSG rules below are a text summary. See the Lucidchart diagram for the authoritative network topology view with CIDR ranges, traffic flows, and NSG rule visualisation.

### 3.3 NSG design

Apply NSGs per subnet with explicit allow rules and deny-by-default behavior.

**Application Gateway subnet NSG**

- Allow inbound `443` from Internet
- Allow inbound `80` only for redirect support
- Allow outbound to ACA internal ingress on required port
- Allow Azure management/service tag traffic required by Application Gateway

**Container Apps infrastructure subnet NSG**

- Allow inbound only from Application Gateway subnet to app ingress
- Allow outbound to:
  - private endpoint subnet
  - Azure Monitor / Log Analytics service tags as required
  - CRL/OCSP/package repositories only if explicitly needed
- Deny direct inbound from Internet

**Private endpoint subnet NSG**

- Allow inbound from ACA subnet and approved management paths
- No Internet-originated inbound
- Restrict east-west movement to only necessary service flows

### 3.4 Private DNS

Use Azure Private DNS zones and link them to the production VNET for:

- MySQL Flexible Server private endpoint
- Key Vault private endpoint
- ACR private endpoint
- Any optional private search service
- Internal Container Apps resolution, if required by the final ACA/Application Gateway design

Private DNS is a mandatory part of the production design; without it, Application Gateway and the Container App will not reliably resolve internal service names.

### 3.5 Compute

- **Azure Container Apps Environment**
  - Plan: **Workload profiles**
  - Ingress posture: **internal**
  - Zone redundancy: enable where regionally supported and cost-justified

- **OpenMRS Container App**
  - Ingress: **internal-only**
  - Target port: **8080**
  - Min replicas: **2**
  - Max replicas: **10**
  - Revision mode: **multiple** for controlled blue-green deployments
  - Initial sizing target:
    - **2 vCPU / 4 GiB** per replica
  - Scaling signals:
    - HTTP concurrency
    - CPU
    - memory
  - Health checks:
    - startup: `/openmrs/health/alive`
    - readiness: `/openmrs/health/alive`
    - liveness: `/openmrs/health/alive`

### 3.6 Application Gateway configuration

- SKU: **Application Gateway WAF v2**
- Public frontend IP: **yes**
- Backend target: **Container Apps internal FQDN**
- Listener configuration:
  - HTTPS listener for production hostname
  - Optional HTTP listener for redirect only
- TLS:
  - Terminate TLS at Application Gateway
  - Use **managed certificate** where supported, otherwise Key Vault-backed certificate
- Routing:
  - HTTP to HTTPS redirect
  - Path-based routing only if future services share the gateway
- Health probe:
  - Protocol: HTTP or HTTPS as configured internally
  - Path: `/openmrs/health/alive`
  - Host header: set to the backend host if required by ACA routing
  - Probe interval/threshold tuned to avoid false positives during warm-up
- WAF:
  - Prevention mode in production
  - OWASP managed rule set enabled
  - Add exclusions only after observing valid traffic blocked by rules
- Custom domain:
  - Map production DNS name to Application Gateway public IP/DNS

### 3.7 Database

Recommended production database:

- **Azure Database for MySQL Flexible Server**
  - Tier: **General Purpose**
  - Upgrade path: **Business Critical** for stricter latency and resilience requirements
  - Connectivity: **private endpoint only**
  - Public access: **disabled**
  - High availability: **zone-redundant** where available
  - Backups: **geo-redundant**
  - Maintenance window: set explicitly
  - Server parameters:
    - enable TLS enforcement
    - tune connection limits and InnoDB settings after load tests

Database guidance:

- Start with MySQL Flexible Server unless formal testing requires PostgreSQL instead
- Keep Liquibase migrations in the application release path
- Treat schema migration as part of deployment readiness, not a manual post-step

### 3.8 Security controls

**Identity**

- Assign a **system-assigned managed identity** to the Container App
- Use managed identity for:
  - Key Vault secret retrieval
  - ACR image pull
- Avoid long-lived registry passwords and app-level credentials in environment variables where secret references are available

**Secrets**

- Use **Azure Key Vault** with:
  - private endpoint
  - public access disabled if operating model allows it
  - RBAC-based access control preferred

**Registry**

- Use **ACR Standard or Premium**
- Prefer **Premium** when private endpoint and enterprise networking are mandatory
- Disable anonymous/public access

**Network exposure**

- No public IP on:
  - Container Apps environment
  - database
  - Key Vault
  - ACR
- Only Application Gateway has public ingress

**Platform security**

- Enable **Microsoft Defender for Containers**
- Apply **Azure Policy** for:
  - required tags
  - approved SKUs/regions
  - private endpoint enforcement where appropriate
  - TLS and diagnostic settings requirements
- Enable diagnostic settings on all production resources

**Web security**

- TLS termination at Application Gateway
- WAF v2 in prevention mode
- HTTP to HTTPS redirect enabled
- Restrict cipher suites and TLS versions to organisation policy

### 3.9 Monitoring and observability

- **Azure Monitor + Log Analytics**
  - platform logs
  - container stdout/stderr
  - ingress diagnostics
- **Container Apps built-in metrics**
  - replica count
  - CPU
  - memory
  - requests
  - restart count
- **Application Insights**: optional but recommended
  - Java agent for request tracing
  - dependency timing
  - exception telemetry
- **Alert rules**
  - 5xx error spike
  - unhealthy backend count in Application Gateway
  - high response time
  - replica saturation / max replica reached
  - database CPU/storage/connections
  - Key Vault secret access failures
  - log ingestion anomalies

### 3.10 Optional search service

Elasticsearch remains optional in production for the first landing zone.

Recommended approach:

1. **Phase 1 production**: run without Elasticsearch if OpenMRS can operate acceptably without it for the target use case
2. **Phase 2**: add a private search tier only after performance and search requirements are validated

If Elasticsearch is required:

- keep it off the public Internet
- place it behind private connectivity
- monitor JVM, heap, disk, and index health separately from the application

---

## 4. Infrastructure as Code

### 4.1 Recommended tool

Use **Bicep** as the primary infrastructure-as-code tool.

Why Bicep:

- Azure-native
- strong support for Container Apps, networking, private endpoints, and policy-driven deployments
- simpler operational model than maintaining an additional state backend
- first-class `what-if` support for change review

### 4.2 Suggested module structure

```text
infra/
  modules/
    networking/
    compute/
    database/
    security/
    monitoring/
    ingress/
  main.bicep
  environments/
    dev.bicepparam
    prod.bicepparam
```

Suggested responsibilities:

- **networking**
  - VNET
  - subnets
  - NSGs
  - private DNS links
- **compute**
  - ACA environment
  - Container App
  - scaling rules
- **database**
  - MySQL Flexible Server
  - backups
  - HA
  - private endpoint
- **security**
  - Key Vault
  - managed identity assignments
  - ACR
  - policy attachments
- **monitoring**
  - Log Analytics
  - diagnostic settings
  - alert rules
- **ingress**
  - Application Gateway
  - WAF policy
  - probes
  - listeners

### 4.3 Parameter files

Maintain separate parameter files for **dev** and **prod** to vary:

- naming suffixes
- SKUs
- scaling limits
- public vs private networking
- certificate references
- backup/HA settings

### 4.4 State management

Bicep deployments use Azure Resource Manager as the control plane, so no separate Terraform-style state file is required.

Recommended operational approach:

- deploy at **resource group** or **subscription** scope as appropriate
- use **deployment stacks** or consistent deployment naming for lifecycle tracking
- run `az deployment group what-if` in CI before apply
- store parameter files in source control
- store secrets only in Key Vault or GitHub environment secrets, never in Bicep parameter files

---

## 5. CI/CD integration

### 5.1 Workflow split

Use **GitHub Actions** with separate workflows:

1. **Infrastructure deployment**
   - validates Bicep
   - runs `what-if`
   - deploys dev/prod infrastructure

2. **Application deployment**
   - builds and pushes container image to ACR
   - updates ACA revision
   - verifies health endpoint

### 5.2 Environment promotion

Recommended progression:

- **dev**
- **staging** (optional but strongly recommended)
- **prod**

Each environment should use:

- separate Azure resources
- separate Key Vault secrets
- separate approval gates

### 5.3 Release strategy

For application rollout, use **blue-green or canary via Azure Container Apps revisions**.

Recommendation:

- keep two revisions during release
- send a small percentage of traffic to the new revision
- validate `/openmrs/health/alive` and smoke tests
- promote to 100% after verification
- roll back by shifting traffic to the previous revision

Container Apps does not use classic deployment slots; **revisions are the preferred Azure-native equivalent** here.

### 5.4 Authentication from GitHub Actions

- Use **GitHub OIDC federated credentials**
- Avoid stored service principal secrets where possible
- Use environment protections for production approvals

---

## 6. Cost estimation

Costs vary significantly by region, storage, retention, and traffic. The following are **rough monthly planning estimates** only and should be validated with the Azure Pricing Calculator before approval.

### 6.1 Development environment rough monthly cost

| Component | Expected monthly range | Notes |
|---|---:|---|
| Container Apps consumption | $10-$60 | Depends on active hours and scale-from-zero behavior |
| MySQL Flexible Server burstable | $20-$60 | Small dev server, low storage |
| ACR Basic | $5-$10 | Small image footprint |
| Key Vault | $1-$5 | Mostly transaction-based |
| Log Analytics | $5-$30 | Depends on log retention and verbosity |
| Optional Elasticsearch | $0-$100+ | Prefer skipping to control cost |
| **Estimated dev total** | **$40-$165** | Without Elasticsearch, often near lower end |

### 6.2 Production environment rough monthly cost

| Component | Expected monthly range | Notes |
|---|---:|---|
| Application Gateway WAF v2 | $200-$500+ | One of the major fixed costs |
| ACA workload profiles | $150-$600+ | Driven by always-on replicas and workload profile size |
| MySQL Flexible Server GP/BC + storage | $250-$900+ | Depends on vCores, HA, storage, backups |
| ACR Standard/Premium | $20-$60+ | Premium if private link requirements apply |
| Key Vault + private endpoint | $10-$30 | Includes private endpoint cost patterns |
| Private endpoints (multiple) | $30-$80 | DB, Key Vault, ACR, optional others |
| Log Analytics / Monitor / App Insights | $50-$250+ | Depends on retention and ingestion volume |
| Defender / Policy overhead | $15-$100+ | Tenant policy and licensing dependent |
| Optional Elasticsearch | $100-$600+ | Strongly workload dependent |
| **Estimated prod total** | **$725-$3,120+** | Excluding large traffic spikes and premium add-ons |

### 6.3 Cost optimisation tips

- Keep **dev** on consumption and allow scale-to-zero
- Delay Elasticsearch until required
- Use shorter log retention in non-production
- Right-size database storage and compute after load testing
- Start production on **General Purpose**, move to **Business Critical** only if proven necessary
- Use App Gateway autoscaling carefully and monitor peak patterns

---

## 7. Migration path

### Phase 1: deploy development environment

- Provision ACR, Key Vault, Log Analytics, ACA consumption environment, and MySQL Flexible Server
- Push the OpenMRS image
- Validate:
  - container starts
  - Liquibase completes
  - `/openmrs/health/alive` returns healthy
  - basic login/application smoke tests succeed

### Phase 2: deploy production networking

- Provision production VNET, subnets, NSGs, private DNS, and private endpoint patterns
- Deploy Application Gateway WAF v2
- Validate internal name resolution and network paths before app deployment

### Phase 3: deploy production services

- Provision ACA workload profiles environment
- Provision MySQL Flexible Server with HA and backups
- Provision Key Vault, ACR, monitoring, Defender, and policy controls
- Deploy OpenMRS with internal ingress only
- Validate end-to-end through Application Gateway health probes

### Phase 4: DNS cutover and go-live

- Import or issue TLS certificate
- Configure custom domain on Application Gateway
- Perform blue-green validation
- Lower DNS TTL ahead of cutover
- Execute go-live window and monitor closely
- Retain rollback plan to previous environment/revision until stable

For the broader sequencing and dependencies, also see [05-migration-phases.md](./05-migration-phases.md).

---

## 8. GitHub issues reference

Create one GitHub issue per work item so infrastructure delivery is traceable and parallelisable.

Suggested issue groups:

- networking
- Application Gateway and WAF
- ACA environment and app deployment
- database provisioning and backup policy
- Key Vault and managed identity
- ACR and image pull permissions
- monitoring, alerts, and dashboards
- CI/CD workflows
- DNS and cutover

Suggested labels:

- `area/infrastructure`
- `area/security`
- `env/dev`
- `env/prod`
- `type/epic`
- `type/task`

---

## Recommended decisions summary

- **Dev**: Azure Container Apps Consumption + public ingress + MySQL Flexible Server public access + low-cost supporting services
- **Prod**: VNET-integrated Azure Container Apps Workload Profiles + internal ingress + Application Gateway WAF v2 + private endpoints everywhere possible
- **Database**: standardise on **Azure Database for MySQL Flexible Server** for the managed Azure path
- **Secrets and auth**: use **managed identity** and **Key Vault**
- **Ingress**: Application Gateway is the only public frontend in production
- **IaC**: use **Bicep** with modular structure and environment parameter files
- **Rollouts**: use **ACA revisions** for blue-green/canary releases

## Open gaps / follow-up decisions

- Confirm whether the target managed production database should be **MySQL** or **PostgreSQL**
- Confirm whether **Elasticsearch** is required in phase 1 or can be deferred
- Confirm whether the organisation requires **hub-spoke** on day 1 or accepts a dedicated flat VNET first
- Confirm certificate ownership model: **Application Gateway managed certificate** vs **custom certificate in Key Vault**
- Confirm whether ACR must be **Premium** from the outset for private link and enterprise governance

## README drift note

This plan introduces a new documentation area under `docs/modernisation-plan/`. If this Azure deployment approach becomes the primary recommended deployment path, the top-level [README.md](../../README.md) should be updated separately with links to this plan after human review.

---

## Tracked Issues

### Phase 0 — Foundation (Azure)

- [ ] #50 — Provision Azure subscription access and resource groups for dev and prod
- [ ] #84 — Create Lucidchart architecture diagrams for Azure dev and production environments

### Phase 2 — Dev Environment

- [ ] #52 — Deploy Azure Container Registry (Basic SKU)
- [ ] #80 — Deploy dev Container Apps Environment (Consumption plan) + MySQL Flexible Server
- [ ] #83 — Deploy Azure Key Vault (dev) and seed secrets
- [ ] #85 — Deploy OpenMRS Core Container App to dev environment

### Phase 4 — Production Infrastructure

- [ ] #51 — Provision production VNET (10.0.0.0/16) and subnet layout
- [ ] #105 — Configure NSGs for all production subnets
- [ ] #106 — Deploy production MySQL Flexible Server with private endpoint and HA
- [ ] #107 — Deploy production Key Vault with private endpoint
- [ ] #108 — Upgrade ACR to Standard SKU with private endpoint
- [ ] #109 — Deploy Container Apps Environment into VNET (Workload profiles)
- [ ] #110 — Deploy OpenMRS Container App with internal-only ingress
- [ ] #111 — Deploy Application Gateway WAF_v2
- [ ] #112 — Configure TLS termination and custom domain on Application Gateway
- [ ] #113 — Configure managed identities and RBAC for Container App → Key Vault / ACR
- [ ] #114 — Set up monitoring, alerting, and dashboards (App Insights + Log Analytics)
- [ ] #115 — Enable Microsoft Defender for Containers and Database
- [ ] #116 — Run production security hardening review and remediation
- [ ] #117 — Create Bicep IaC modules for all production resources
- [ ] #118 — Create infrastructure deployment GitHub Actions workflow (OIDC auth)
- [ ] #119 — Create application deployment GitHub Actions workflow

### Phase 6 — Go-Live (Azure)

- [ ] #23 — Create DNS cutover and rollback plan for Azure go-live
- [ ] #49 — Execute DNS cutover and go-live
- [ ] #64 — Post-go-live monitoring — 48-hour intensive monitoring period
