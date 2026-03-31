---
description: Create a Delivery Loop plan — map work to scenario profile, identify context sources, plan phase sequence, and assign agents
argument-hint: "Describe the work to plan and any known constraints"
---

# Plan Delivery Loop

You are creating a Delivery Loop plan without executing it. This produces a structured plan document that can be reviewed, refined, and executed later.

## 1. Analyse the work

From the input: ${input:description}

- What is being delivered? (feature, fix, improvement, migration)
- What is the scope? (files, modules, services affected)
- What are the constraints? (timeline, dependencies, risks)

## 2. Identify scenario profile

Determine whether this is Greenfield, Brownfield, or Modernisation:

- Examine the target codebase and existing context
- For Modernisation: identify both as-is and to-be contexts
- State the profile with justification

## 3. Map context sources

List the context sources that will be read during execution:

| Phase | Context to read | Source location |
|---|---|---|
| Requirements | <what business/product context> | <where it lives> |
| Implementation | <what technical context> | <where it lives> |
| Quality | <what standards/policies> | <where it lives> |

## 4. Plan phases

For each phase, specify:

- **Owner**: which agent will lead
- **Key tasks**: what work will be done
- **Exit criteria**: what "done" looks like
- **Context write**: what documentation/knowledge will be updated

## 5. Identify risks and dependencies

- Cross-phase dependencies
- External dependencies (APIs, services, teams)
- Risk areas requiring extra attention

## 6. Present for approval

Present the complete plan to the human. They choose:
- **Refine** — adjust the plan based on feedback
- **Create Task** — save as a GitHub Issue for async execution
- **Start** — begin executing immediately via `/delivery.start`
