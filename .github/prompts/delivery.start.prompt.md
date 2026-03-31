---
description: Start a full Delivery Loop cycle — identify scenario, read context, and execute Requirements → Implementation → Quality with human gates
argument-hint: "Describe the feature, fix, or improvement to deliver"
---

# Start Delivery Loop

You are initiating a full Delivery Loop cycle. Follow these steps:

## 1. Identify scenario profile

Analyse the work and codebase to determine the scenario profile:

- **Greenfield** — new project or component with no existing code/context
- **Brownfield** — change to an existing codebase with established patterns
- **Modernisation** — transforming existing system architecture (dual context: as-is + to-be)

State the profile and justify your choice.

## 2. Read context

Based on the scenario profile, identify and read relevant context sources:

- Business documentation, requirements, stakeholder input
- Technical documentation, architecture decisions, coding standards
- Existing codebase, test suites, CI/CD configuration
- Work items, backlog, acceptance criteria

List what you consulted.

## 3. Execute phases

Run through each phase in order, requesting human approval at each gate:

### Phase 1 — Requirements
Delegate to the business-analyst (or best available requirements specialist) to:
- Discover and refine requirements from the input: ${input:description}
- Produce structured, testable work items with acceptance criteria
- **Gate**: Present work items to human for approval before proceeding

### Phase 2 — Implementation
Delegate to the tech-lead to orchestrate:
- Plan the implementation (tech-lead produces plan, human approves)
- Implement code changes (developer, infrastructure)
- Test (testing-engineer defines strategy, testing-automation implements tests)
- Document (tech-writer updates affected docs)
- **Gate**: Present implementation outcome to human for approval before proceeding

### Phase 3 — Quality
Delegate to the pr-reviewer to:
- Review all changes for correctness, conventions, security
- Validate requirements adherence
- **Gate**: Approve changes or send back with specific, actionable feedback

## 4. Write context

After all phases complete, verify the context contract:
- All documentation updated to reflect changes
- Work items updated with outcomes
- Any new knowledge captured for the Learning Loop
