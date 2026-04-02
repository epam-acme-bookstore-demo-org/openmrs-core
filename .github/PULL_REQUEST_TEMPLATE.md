<!--- Add a pull request title above in this format -->
<!--- real example: 'TRUNK-5111: Replace use of deprecated isVoided' -->
<!--- 'TRUNK-JiraIssueNumber: JiraIssueTitle' -->

## Description

### What
<!--- What changed? -->

### Why
<!--- Why is this change needed? -->

### How
<!--- How was this implemented? Include notable trade-offs if helpful. -->

## Type of change

<!--- Put an `x` in the box(es) that apply -->
- [ ] Bug fix
- [ ] Feature
- [ ] Refactor
- [ ] Modernisation
- [ ] Documentation
- [ ] Infrastructure / CI

## Issue I worked on
<!--- This project only accepts pull requests related to open issues -->
<!--- Want a new feature or change? Discuss it in an issue first! -->
<!--- Found a bug? Point us to the issue/or create one so we can reproduce it! -->
<!--- Add the related GitHub issue and/or Jira ticket -->
- GitHub issue: #<issue-number>
- Jira issue: see https://issues.openmrs.org/browse/TRUNK-

## General checklist
<!--- Put an `x` in the box if you did the task -->
- [ ] My IDE is configured to follow the [**code style**](https://wiki.openmrs.org/display/docs/Java+Conventions) of this project.
- [ ] I ran `mvn clean package` right before creating this pull request and added all formatting changes to my commit.
- [ ] All new and existing tests passed.
- [ ] My pull request is based on the latest changes of the master branch.

## Java 21 Modernisation Checklist

<!--- Only applies to PRs marked as modernisation -->
- [ ] New code uses `var` where appropriate (per coding standards §2)
- [ ] Text blocks used for multi-line strings (per coding standards §3)
- [ ] Switch expressions used instead of switch statements where applicable (per coding standards §4)
- [ ] Pattern matching for `instanceof` used instead of explicit casts (per coding standards §5)
- [ ] Records used for value objects/DTOs where appropriate (per coding standards §6)
- [ ] `java.time` used instead of `java.util.Date` / `Calendar` (per coding standards §9)
- [ ] No new broad `catch (Exception e)` — specific exceptions caught (per coding standards §14)
- [ ] No new boolean parameters — use enums or builder pattern (per coding standards §15)
- [ ] No new methods with 5+ parameters (per coding standards §15)
- [ ] OpenRewrite was considered for mechanical changes

## Testing checklist

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated (if applicable)
- [ ] All existing tests pass
- [ ] Coverage not decreased

## Documentation checklist

- [ ] Javadoc updated for public API changes
- [ ] Architecture docs updated (if structural changes)
- [ ] CHANGELOG updated (if user-facing changes)

## Reviewer guidance

- Review Java 21 and modernisation changes against [docs/java21-coding-standards.md](docs/java21-coding-standards.md).
- The modernisation CI workflow at `.github/workflows/ci-modernisation.yml` runs build, Checkstyle, SpotBugs, and coverage checks.
