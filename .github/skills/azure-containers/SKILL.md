---
name: azure-containers
description: Azure container platform standards — AKS, Container Apps, ACR, workload identity, and ingress patterns
license: MIT
---

# Azure Container Platform Standards

Use this skill when provisioning or managing container workloads on Azure.

## Azure Kubernetes Service (AKS)

- Use system and user node pools — isolate system workloads from application workloads.
- Enable managed identity for the cluster — avoid service principal rotation.
- Use Azure CNI Overlay or Azure CNI with dynamic IP allocation for production networking.
- Enable cluster autoscaler on user node pools with appropriate min/max bounds.
- Use Availability Zones for production clusters.
- Enable Azure Policy add-on for governance (pod security, image restrictions).
- Enable Microsoft Defender for Containers for runtime threat detection.
- Use maintenance windows for controlled node image upgrades.
- Prefer managed NGINX ingress controller or Application Gateway for Containers (AGIC).

## Azure Container Apps (ACA)

- Use Container Apps for HTTP-driven microservices and event-driven workloads that don't need full Kubernetes control.
- Configure scaling rules based on HTTP concurrency, queue length, or custom metrics.
- Use revisions for traffic splitting and gradual rollouts.
- Enable Dapr sidecar only when using service-to-service invocation, pub/sub, or state management.
- Use Container Apps Environments for network and resource isolation.
- Connect to VNet for private networking with backend services.

## Azure Container Registry (ACR)

- Use Premium SKU for geo-replication, private endpoints, and content trust.
- Enable ACR Tasks for automated image builds triggered by source commits or base image updates.
- Configure image purge policies to control storage costs.
- Attach ACR to AKS with managed identity (avoid admin credentials).
- Use repository-scoped tokens for CI/CD pipelines.
- Enable vulnerability scanning (Microsoft Defender for Containers).

## Workload identity

- Use Azure Workload Identity (federated credentials) for pod-to-Azure-service authentication.
- Create a `ServiceAccount` annotated with `azure.workload.identity/client-id`.
- Configure federated identity credential on the managed identity linking to the OIDC issuer.
- Never mount service principal secrets as pod environment variables — use workload identity instead.
- Test identity access with non-destructive read operations before deploying workloads.

## Ingress and networking

- Use Application Gateway for Containers (AGIC) for L7 load balancing with WAF.
- Use managed NGINX ingress controller for standard HTTP routing.
- Terminate TLS at the ingress controller — use Azure Key Vault for certificate management.
- Use internal load balancers for workloads not exposed to the internet.
- Define Network Policies to restrict east-west traffic between pods.
- Use Private Link for connections to Azure PaaS services (SQL, Storage, Key Vault).

## Monitoring

- Enable Container Insights for log and metric collection from AKS clusters.
- Use Azure Monitor managed service for Prometheus for metrics.
- Configure Azure Managed Grafana for dashboards.
- Set alerts on node resource pressure, pod restart counts, and failed deployments.
- Use diagnostic settings to stream control plane logs to Log Analytics.
