---
description: "Technology routing for the Java squad"
applyTo: "**"
---

# Squad Routing — Java

This squad specialises the Delivery Loop for Java applications. The Delivery Loop provides the workflow orchestration (phases, gates, context contracts) — this instruction adds technology-specific routing.

## Technology routing

When the Delivery Loop delegates work, route tasks based on technology:

- **Java** code → **developer** with Java skills (Spring Boot, Maven/Gradle, Bean validation, JPA/Hibernate)
- **Infrastructure** → **infrastructure** with whatever cloud/IaC skills are installed (Azure, AWS, Terraform, etc.)
- **Documentation** → **tech-writer** for all docs, runbooks, and knowledge articles affected by changes

## Extending this squad

This squad provides base Java skills. Layer additional packages for your specific stack:

- **Angular frontend** → add `frontend/angular` for component patterns, RxJS, routing
- **React frontend** → add `frontend/react` for component patterns, hooks, state management
- **Azure infrastructure** → add `infrastructure/azure` + `infrastructure/pulumi` for cloud IaC
- **Terraform** → add `infrastructure/terraform` for HCL-based IaC
- **Docker** → add `devops/docker` for container build workflows
- **Testcontainers** → add `testing/testcontainers` for integration testing with real dependencies

## Parallelism guidance

- **Developer** and **infrastructure** may work in parallel when their contracts are already clear; otherwise infrastructure changes define the boundary first.
- **Testing** sets coverage expectations early — before implementation starts.
