---
name: java-standards
description: Java development standards — conventions, modern Java features, version migration, container readiness, and testing
license: MIT
---

# Java Development Standards

Use this skill when implementing, reviewing, or testing Java code.

## Coding conventions

- Follow project conventions (Maven or Gradle, Spring or Quarkus).
- Use proper access modifiers; prefer immutability (`final`, records).
- Handle exceptions with specific types; avoid catching `Exception` directly.
- Use `Optional` for nullable returns; avoid returning `null`.
- Follow existing project structure for packages, naming, and layering.
- Use `@Override`, `@Nullable`, `@NonNull` annotations where appropriate.

## Documentation

- Document JDK version requirements and setup.
- Include Maven/Gradle commands for build, test, and run.
- Document Spring profiles, configuration properties, and environment variables.
- Include API documentation references (Swagger/OpenAPI).

## Testing

- Use JUnit 5 with `@DisplayName` for readable test names.
- Use Mockito for mocking; mock at boundaries, not internal collaborators.
- Use AssertJ for fluent, readable assertions.
- Integration tests with `@SpringBootTest` or Testcontainers where appropriate.
- Run `mvn verify` or `gradle test` and report coverage.

## Validation

- Standard local validation: `mvn verify` or `gradle build`.
- Coverage targets: Lines >= 80%, Branches >= 70%.
- Enforce consistent build tool usage (Maven or Gradle, not both).

## Modern Java (17+)

- Use records for DTOs and value objects.
- Use sealed classes and interfaces for constrained hierarchies.
- Use pattern matching for `instanceof` and switch expressions.
- Use text blocks for multi-line strings (SQL, JSON, templates).
- Use switch expressions with arrow syntax.
- Use `var` for local variable type inference where the type is obvious.
- Use sequenced collections (`SequencedCollection`, `SequencedMap`).
- Use virtual threads (`Thread.ofVirtual()`) for I/O-bound workloads — prefer over platform threads for blocking operations.
- Prefer the newer `java.time` API over legacy date/time classes.

## Version migration

- Use `--release` flag (Maven: `<release>21</release>`, Gradle: `toolchain { languageVersion = JavaLanguageVersion.of(21) }`) to enforce source/target compatibility.
- Audit dependency compatibility before upgrading — check library release notes for JDK support.
- Address removed APIs: check `jdeprscan` output for deprecated/removed usage.
- Handle module system (`--add-opens`, `--add-exports`) only as temporary workarounds; prefer updating libraries.
- Update build plugins (compiler, surefire, failsafe) to versions supporting the target JDK.
- Run full test suite on target JDK before merging; watch for reflection and serialization changes.

## Container readiness

- Use `-XX:MaxRAMPercentage=75.0` instead of fixed `-Xmx` to adapt to container memory limits.
- Java 17+ is CGroup v2 aware — container CPU and memory limits are respected automatically.
- Configure graceful shutdown: Spring Boot `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase`.
- Expose health endpoints: `/actuator/health/liveness` and `/actuator/health/readiness` for container probe integration.
- Externalise configuration via environment variables and/or mounted config files — not embedded properties.
- Use multi-stage Docker builds: build with JDK image, run with JRE/distroless image.
- Prefer `jlink` custom runtime images for minimal container footprint when no dynamic classloading is needed.
- Set `-XX:+UseContainerSupport` (enabled by default in Java 17+) — do not disable it.
