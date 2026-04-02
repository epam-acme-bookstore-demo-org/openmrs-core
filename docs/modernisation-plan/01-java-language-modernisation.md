# Java Language Modernisation Plan

This document defines how OpenMRS Core should move from “Java 21 for compilation” to “Java 21 in everyday application code”.

- Back to the main project guide: [`README.md`](../../README.md)
- Next planning document: [`05-migration-phases.md`](./05-migration-phases.md)

## Scope

OpenMRS Core `3.0.0-SNAPSHOT` already compiles with Java 21 (`maven.compiler.release=21` in the root `pom.xml`), and the framework stack is already compatible with modern Java:

- Spring 7
- Hibernate 7
- Jakarta EE

This plan is therefore **not** about framework upgrades. It is about **modernising application code patterns** across the repository so that OpenMRS Core actually uses Java 9-21 language features and standard APIs.

## Non-goals

This document does **not** propose:

- a switch to preview language features
- a rewrite of stable public APIs without compatibility review
- a blanket conversion of JPA entities to records
- Java Platform Module System adoption unless a separate architectural decision is made

---

## 1. Assessment Summary

### Current baseline

Verified from the current repository:

- The root build sets `maven.compiler.release=21`.
- The Maven reactor declares 8 top-level modules: `bom`, `tools`, `test`, `api`, `web`, `webapp`, `liquibase`, `test-suite`.
- `api` is by far the largest Java code surface.
- `org/openmrs/util/OpenmrsSecurityManager.java` still extends `SecurityManager`.
- `org/openmrs/util/HttpClient.java` still wraps `HttpURLConnection`.
- Spotless, Checkstyle, and SpotBugs are already part of the build and should remain the guardrails for all migration work.

### Gap between “compiles on Java 21” and “uses Java 21 well”

OpenMRS Core is functionally on Java 21, but stylistically much closer to Java 8. The codebase still shows broad use of:

- explicit casting after `instanceof`
- verbose local variable declarations
- statement-style `switch`
- string concatenation for multi-line SQL and other structured text
- legacy `java.util.Date`
- older collection creation patterns
- older Stream and Optional idioms
- deprecated runtime mechanisms such as `SecurityManager`

### Estimated current state

The exact counts will move over time, but the planning baseline is:

| Area | Current state | Scope / implication |
|---|---:|---|
| Java release target | Java 21 | Build baseline is already correct |
| `var` adoption | Minimal | Useful readability cleanup remains across most modules |
| Records | 0 in production code | Candidate only for DTOs/value carriers, not entities |
| Sealed classes | 0 | Requires hierarchy-by-hierarchy review |
| Text blocks | 0 in main code | Good fit for SQL/XML/multi-line literals |
| Switch expressions | 0 | Low-risk cleanup where `switch` assigns or returns a value |
| Pattern matching `instanceof` | 0 | High-volume quick win in validators, `equals()`, converters |
| `java.util.Date` usage | ~268 files in planning inventory; local repo check found 262 direct imports | Multi-sprint migration requiring API boundary strategy |
| SecurityManager | Present | Must be removed before Java 24-era compatibility becomes urgent |
| Raw types | ~11 likely files | Targeted cleanup, usually low effort |
| Stream API | Underused newer methods | Incremental opportunistic cleanup |
| Collection factories | Underused | Replace verbose immutable collection setup where safe |
| Optional improvements | Underused | Apply only where it improves clarity |
| String API improvements | Underused | Straightforward low-risk replacements |
| Helpful NPE messages | Available by default on Java 14+ | No migration needed; avoid obscuring them |
| `HttpClient` | Legacy wrapper still present | Design decision needed before broad replacement |

### Overall conclusion

The modernization gap is real but manageable because it naturally splits into three kinds of work:

1. **High-volume syntax cleanups** with low behavioral risk.
2. **Type and API migrations** that need targeted design review.
3. **Legacy runtime assumptions** that must be retired deliberately.

The largest single effort is the `java.util.Date` to `java.time` migration in `api`, which should be treated as a dedicated multi-sprint workstream rather than a “cleanup task”.

---

## 2. Modernisation Categories and Priorities

### Priority 1 — High-impact, low-risk

These changes are good candidates for incremental, module-by-module PRs. They improve readability and reduce boilerplate without changing architecture.

### 2.1 Pattern matching for `instanceof`

**Why first:** The codebase still uses classic check-then-cast patterns heavily, especially in `equals()`, validators, and adapter logic.

**Apply when:**

- a variable is checked with `instanceof` and immediately cast
- the cast variable is only needed in the guarded branch

**Before**

```java
if (obj instanceof BaseOpenmrsObject) {
    BaseOpenmrsObject other = (BaseOpenmrsObject) obj;
    return uuid != null && uuid.equals(other.getUuid());
}
return false;
```

**After**

```java
if (obj instanceof BaseOpenmrsObject other) {
    return uuid != null && uuid.equals(other.getUuid());
}
return false;
```

**Good targets**

- `equals()` implementations
- validator/helper branches
- exception/cause handling
- collection element checks

### 2.2 `var` for local variables

**Why first:** This is an easy readability improvement when the right-hand side already makes the type obvious.

**Apply when:**

- the initializer makes the type obvious
- the variable has local scope
- the declaration becomes shorter and easier to read

**Before**

```java
Map<String, String> properties = new LinkedHashMap<>();
PatientIdentifier identifier = patientService.getPatientIdentifier(id);
```

**After**

```java
var properties = new LinkedHashMap<String, String>();
var identifier = patientService.getPatientIdentifier(id);
```

**Do not use when:**

- the type would become unclear
- the initializer is `null`
- the variable participates in complex generic inference that becomes harder to read
- the code is already concise and explicit typing adds clarity

### 2.3 Switch expressions

**Why first:** They remove mutable temporary variables and accidental fall-through.

**Before**

```java
String label;
switch (status) {
    case ACTIVE:
        label = "active";
        break;
    case RETIRED:
        label = "retired";
        break;
    default:
        label = "unknown";
}
return label;
```

**After**

```java
return switch (status) {
    case ACTIVE -> "active";
    case RETIRED -> "retired";
    default -> "unknown";
};
```

**Good targets**

- methods returning a value from `switch`
- branches that only assign a local variable
- enum-based branching

### 2.4 Text blocks

**Why first:** They improve long SQL, XML, JSON, HTML, and error templates immediately.

**Before**

```java
String sql = "SELECT person_id, uuid, birthdate " +
        "FROM person " +
        "WHERE voided = 0 " +
        "AND uuid = ?";
```

**After**

```java
String sql = """
        SELECT person_id, uuid, birthdate
        FROM person
        WHERE voided = 0
          AND uuid = ?
        """;
```

**Good targets**

- SQL in DAOs and migration helpers
- XML fragments
- HTML snippets in tests
- long log/debug templates

### 2.5 Enhanced `for` with `var`

**Before**

```java
for (Map.Entry<String, Object> entry : attributes.entrySet()) {
    handle(entry.getKey(), entry.getValue());
}
```

**After**

```java
for (var entry : attributes.entrySet()) {
    handle(entry.getKey(), entry.getValue());
}
```

Use this only when the element type is obvious from the iterable.

### 2.6 String API improvements

**Why first:** These are small, safe replacements that reduce custom trimming and splitting logic.

**Before**

```java
if (value == null || value.trim().isEmpty()) {
    return;
}

String message = "User " + username + " has role " + role;
```

**After**

```java
if (value == null || value.isBlank()) {
    return;
}

String message = "User %s has role %s".formatted(username, role);
```

**Preferred modern APIs**

- `isBlank()`
- `strip()`
- `lines()`
- `repeat()`
- `indent()`
- `formatted()`

### Priority 1 delivery guidance

- Prefer small PRs by package or module.
- Favor mechanical cleanups with no semantic changes.
- Do not mix syntax cleanup with `java.time` or design changes in the same PR.

---

### Priority 2 — Medium-impact, moderate-risk

These changes provide stronger modelling or API improvements, but they require deliberate review.

### 2.7 Records for DTOs, value objects, and internal carriers

**Use records for:**

- immutable DTOs
- query/result carriers
- internal command/result structures
- composite keys or value objects that are not JPA entities

**Do not convert:**

- JPA/Hibernate entities
- proxied framework types
- classes with mutable lifecycle semantics
- public extension points where constructor shape is part of compatibility expectations

**Before**

```java
public final class ValidationResult {
    private final boolean valid;
    private final String message;

    public ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public boolean valid() {
        return valid;
    }

    public String message() {
        return message;
    }
}
```

**After**

```java
public record ValidationResult(boolean valid, String message) { }
```

### 2.8 Sealed classes for closed hierarchies

**Use sealed classes only when:**

- the set of subtypes is intentionally closed
- all implementations are controlled within OpenMRS Core
- closing the hierarchy simplifies branching and validation

**Before**

```java
public abstract class AuthScheme { }

public final class BasicAuthScheme extends AuthScheme { }
public final class TokenAuthScheme extends AuthScheme { }
```

**After**

```java
public sealed abstract class AuthScheme
        permits BasicAuthScheme, TokenAuthScheme { }

public final class BasicAuthScheme extends AuthScheme { }
public final class TokenAuthScheme extends AuthScheme { }
```

**Do not seal:**

- public SPI types intended for module authors
- framework-managed hierarchies
- types likely to gain community extensions

### 2.9 `java.util.Date` to `java.time`

This is the largest modernization stream in the repository and should be planned explicitly.

#### Migration strategy

1. **Inventory first** by package and usage type.
2. **Choose the right replacement type**, not a blanket `Instant`.
3. **Migrate internal logic before public boundaries**.
4. **Keep adapters at boundaries temporarily** where database mappings or public APIs still expose `Date`.
5. **Move package-by-package**, with focused regression tests.

#### Type selection rules

| Current meaning | Preferred type |
|---|---|
| Date only, no time-of-day | `LocalDate` |
| Machine timestamp / event instant | `Instant` |
| Date and time without zone semantics | `LocalDateTime` |
| Date and time where zone/offset matters | `OffsetDateTime` or `ZonedDateTime` |

#### Before

```java
private Date encounterDatetime;

public Date getEncounterDatetime() {
    return encounterDatetime;
}
```

**After**

```java
private Instant encounterDatetime;

public Instant getEncounterDatetime() {
    return encounterDatetime;
}
```

#### Temporary boundary adapter

```java
public static Instant toInstant(Date date) {
    return date == null ? null : date.toInstant();
}

public static Date toDate(Instant instant) {
    return instant == null ? null : Date.from(instant);
}
```

#### Additional cautions

- JPA mappings, database columns, REST payloads, and serialized forms must be reviewed together.
- Do not mix `Date` and `java.time` in the same internal domain path longer than necessary.
- Prefer one bounded context at a time: e.g. person data, encounter data, visit data.

### 2.10 Collection factory methods

**Before**

```java
List<String> roles = Collections.unmodifiableList(Arrays.asList("ADMIN", "PROVIDER"));
Map<String, Integer> counts = new HashMap<>();
counts.put("patients", 1);
counts.put("visits", 2);
```

**After**

```java
List<String> roles = List.of("ADMIN", "PROVIDER");
Map<String, Integer> counts = Map.of("patients", 1, "visits", 2);
```

**Use when:**

- the collection is small
- the content is fixed
- `null` elements are not required

### 2.11 Stream API modernisation

**Before**

```java
List<String> names = patients.stream()
        .map(Patient::getUuid)
        .collect(Collectors.toList());
```

**After**

```java
List<String> names = patients.stream()
        .map(Patient::getUuid)
        .toList();
```

**Potential advanced replacement**

```java
List<String> names = patients.stream()
        .<String>mapMulti((patient, downstream) -> {
            if (patient.getNames() != null) {
                patient.getNames().forEach(name -> downstream.accept(name.getFullName()));
            }
        })
        .toList();
```

**Guidance**

- Prefer `.toList()` over `Collectors.toList()` when an unmodifiable result is acceptable.
- Use `mapMulti()` only when it is clearer than `flatMap()`.
- Do not force stream rewrites where a loop is easier to read.

---

### Priority 3 — Low-impact or high-risk

These tasks matter, but they either need architecture decisions or produce less day-to-day code benefit.

### 2.12 SecurityManager removal

`api/src/main/java/org/openmrs/util/OpenmrsSecurityManager.java` still extends `SecurityManager`.

That was already deprecated in Java 17 and is being removed from application use in newer Java releases, so it should not remain a long-term dependency.

**Likely replacement:** `StackWalker`

**Before**

```java
OpenmrsSecurityManager securityManager = new OpenmrsSecurityManager();
Class<?> caller = securityManager.getCallerClass(1);
```

**After**

```java
Class<?> caller = StackWalker
        .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .walk(frames -> frames
                .skip(1)
                .findFirst()
                .orElseThrow()
                .getDeclaringClass());
```

**Important:** this is not a mechanical swap. Call-depth assumptions must be re-tested.

### 2.13 `java.net.http.HttpClient` migration

`org.openmrs.util.HttpClient` currently wraps `HttpURLConnection`. That should be reviewed, but not auto-replaced everywhere without understanding:

- timeout behavior
- redirect handling
- testability
- existing call sites
- public/service contracts

**Before**

```java
HttpURLConnection connection = url.openConnection();
connection.setRequestMethod("POST");
```

**After**

```java
var client = java.net.http.HttpClient.newHttpClient();
var request = java.net.http.HttpRequest.newBuilder(uri)
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .build();

var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
```

### 2.14 Optional API improvements

Use these opportunistically, not dogmatically.

**Before**

```java
if (result.isPresent()) {
    handle(result.get());
} else {
    handleMissing();
}
```

**After**

```java
result.ifPresentOrElse(this::handle, this::handleMissing);
```

Also consider:

- `or(...)`
- `stream()`

Avoid `Optional` fields, JPA entity fields, and method parameters.

### 2.15 Module system adoption

**Recommendation:** not worth pursuing now.

Reasons:

- heavy use of frameworks and reflection
- limited direct benefit to current delivery goals
- migration cost is high
- it does not help the primary modernization gaps listed above

Revisit only if there is a separate need for runtime encapsulation, custom runtime images, or stronger dependency boundaries.

---

## 3. Module-by-Module Approach

The reactor has 8 top-level modules, but this plan focuses on the **7 implementation modules** that can materially affect Java language modernization. The `bom` module is dependency-management only and should only be touched if build rules or plugin configuration must support this plan.

### Recommended overall sequence

1. `tools` — confirm there is effectively no Java code to modernize
2. `test` — confirm dependency-only scope and update guidance if needed
3. `liquibase` — small pilot for Priority 1 syntax changes
4. `test-suite` — small pilot for CI and review workflow
5. `api` — the main modernization stream
6. `web` — apply patterns proven in `api`
7. `webapp` — resource/config review and any adjacent Java cleanup

### Module plan

| Module | Estimated scale | Suggested order | Module-specific considerations |
|---|---|---|---|
| `tools` | Very small; current repository view shows resources but no meaningful Java surface | 1 | Mostly resources and legacy build assumptions. Confirm no code migration is needed; document exceptions if found. |
| `test` | Very small; currently dependency-only | 2 | Focus on test-support conventions, not language rewrites. Keep this module aligned with modernization examples used in other modules. |
| `liquibase` | Small; current repo check found 8 Java files | 3 | Good first real code target for `instanceof`, switch cleanup, text blocks, and try-with-resources improvements. |
| `test-suite` | Small; current repo check found 12 Java files across nested modules | 4 | Good proving ground for patterns before larger production modules. Use it to validate CI, formatting, and review expectations. |
| `api` | Very large; current repo check found 1,184 Java files and ~260 direct `Date` imports | 5 | Main risk area. Contains most Java files and almost all `Date` usage. Split into multiple issues and PRs by theme or package. |
| `web` | Medium; current repo check found 68 Java files and 2 direct `Date` imports | 6 | Good follow-on module once `api` conventions are stable. Expect validators, filters, and controller helpers to benefit from Priority 1 changes. |
| `webapp` | Small for Java language; current repository contents are mostly WAR resources/config | 7 | Mostly WAR resources and configuration. Java-language modernization will be limited; avoid forcing changes where there is no Java benefit. |

### Detailed notes by module

#### `tools`

- Current repository contents indicate little or no Java source.
- Treat as a verification pass, not a refactor target.
- If any Java code is added later, apply the same rules from this plan from day one.

#### `test`

- This module is currently dependency-focused.
- Use it to keep test libraries and examples aligned with modern idioms used elsewhere.
- Do not create style drift by modernizing production code but leaving test examples outdated.

#### `liquibase`

- Small enough for early wins.
- Good candidates:
  - pattern matching `instanceof`
  - switch expressions
  - text blocks for SQL strings
  - try-with-resources cleanup
- Ideal as the first reviewable “real” modernization PR.

#### `test-suite`

- Small enough to validate how modernization changes behave in more integrated scenarios.
- Useful for experimenting with modern test helper code without risking the core domain model.
- Prefer Priority 1 and small Stream/String improvements here.

#### `api`

- This is the center of the migration effort.
- It contains:
  - the dominant Java surface area
  - most old-style `instanceof` patterns
  - almost all `Date` imports
  - legacy utility classes such as `OpenmrsSecurityManager` and `HttpClient`
- Split work into dedicated issue streams:
  - syntax quick wins
  - string and collection updates
  - stream cleanup
  - records candidate inventory
  - sealed hierarchy review
  - `java.time` migration by domain package
  - SecurityManager removal
  - HTTP client replacement decision

#### `web`

- Moderate scope and lower risk than `api`.
- Apply conventions already proven in `api`.
- Good targets:
  - validators
  - request/response helpers
  - XML/filter parsing helpers
  - string-heavy logic

#### `webapp`

- Primarily packaging and web resources.
- Expect limited Java-language changes.
- Most value will be from keeping docs, config, and examples aligned with the rest of the plan.

---

## 4. Migration Rules

These rules are intended to keep modernization changes consistent and reviewable.

### 4.1 General rules

1. Prefer **small, behavior-preserving** changes.
2. Do not combine style cleanups with business logic changes.
3. Do not modernize a touched block halfway; finish the local pattern consistently.
4. Keep public API compatibility unless the issue explicitly allows a breaking change review.
5. Use the existing formatting and static analysis toolchain as the source of truth.

### 4.2 Feature-specific rules

| Feature | Apply when | Avoid when |
|---|---|---|
| Pattern matching `instanceof` | A cast follows the type check | The checked value must stay available in multiple incompatible branches |
| `var` | RHS type is obvious and readability improves | Type becomes hidden, generic intent becomes unclear, or initializer is `null` |
| Switch expressions | `switch` returns or assigns a value | Intentional fall-through is required or statement style is clearer |
| Text blocks | Literal is multi-line or structurally formatted | Single-line strings or indentation would become confusing |
| Records | Immutable carrier/value type with no JPA/proxy concerns | Entities, mutable domain objects, compatibility-sensitive extension points |
| Sealed classes | Hierarchy is intentionally closed and internally owned | Public extension points or framework-generated/proxied types |
| `java.time` | New domain work or bounded migration area | Mixed partial conversion without boundary adapters |
| `List.of()` / `Map.of()` / `Set.of()` | Small immutable collections with no `null` values | Mutable collections or collections that may contain `null` |
| Stream `.toList()` | Unmodifiable result is acceptable | Callers rely on mutability |
| `Optional` enhancements | Control flow becomes simpler | It reads as “clever” rather than clear |

### 4.3 Anti-patterns to avoid

#### Avoid unclear `var`

**Do not**

```java
var value = someService.getResult();
```

if the reader cannot immediately infer the type.

#### Avoid record conversion of entities

**Do not**

- convert Hibernate/JPA entities into records
- convert types that rely on setters, proxies, or framework-managed mutation

#### Avoid “stream for the sake of stream”

Use a loop when it is clearer, especially for:

- checked exceptions
- complex branching
- side-effect-heavy code

#### Avoid mixed date APIs in new code

New or heavily touched internal code should not introduce new `Date` usage if `java.time` can be used instead.

### 4.4 Style and tooling guardrails

All modernization changes must continue to satisfy the existing repository standards:

- **Spotless** (`spotless-maven-plugin`)
- **Checkstyle** (`maven-checkstyle-plugin`)
- **SpotBugs** (`spotbugs-maven-plugin`)

Formatting is already driven by the repository’s Spotless + Eclipse formatter setup. Do not hand-format code against those tools.

---

## 5. Validation Approach

### Required checks for every migration PR

1. All existing tests must continue to pass.
2. Spotless must pass.
3. Checkstyle must pass.
4. SpotBugs findings must not increase.
5. No behavior changes should be introduced unless explicitly called out in the linked issue.

### PR structure

- Each PR should stay within **one module** for reviewability.
- Large modules such as `api` will still require **multiple PRs**, but each PR should remain module-scoped and topic-scoped.
- Do not mix `api` and `web` modernization in the same PR.
- Do not mix syntax-only PRs with `java.time` migration PRs.

### Validation expectations by change type

| Change type | Minimum validation |
|---|---|
| Syntax cleanup (`instanceof`, `var`, switch expressions, String API) | Existing tests + formatting/static analysis |
| Records / sealed classes | Existing tests + targeted construction/serialization/equality tests |
| `java.time` migration | Existing tests + boundary/serialization/database mapping review |
| SecurityManager removal | Existing tests + targeted stack/caller resolution tests |
| HTTP client replacement | Existing tests + request/timeout/redirect behavior tests |

### Suggested review checklist

- Is the change behavior-preserving?
- Is the modern Java feature actually clearer than the previous code?
- Does the PR stay within the agreed module and migration category?
- Are edge cases around nullability, mutability, serialization, persistence, or proxies still covered?
- Has any API compatibility risk been called out explicitly?

### Test baseline

Planning should assume that the existing test estate remains the safety net. The migration must preserve the current test baseline, including the 359 tests identified in the modernization inventory and any additional module-specific tests present in the repository.

---

## 6. GitHub Issues Reference

This document is the **policy and context document**. Execution should happen through individual GitHub issues.

### Issue model

Create one GitHub issue per migration task, for example:

- `tools`: verify no Java modernization work is needed
- `test`: align test-support examples with modern Java style
- `liquibase`: Priority 1 syntax modernization
- `test-suite`: Priority 1 modernization
- `api`: pattern matching `instanceof`
- `api`: switch expressions and String API improvements
- `api`: records candidate review
- `api`: `java.time` migration for person/encounter/visit packages
- `api`: remove `OpenmrsSecurityManager`
- `api`: evaluate `java.net.http.HttpClient`
- `web`: Priority 1 modernization
- `webapp`: resource/config follow-up

### Required issue contents

Each issue should include:

- module name
- migration category and priority
- explicit in-scope patterns
- explicit out-of-scope patterns
- validation requirements
- link back to this document
- link forward to the phase planning document once available

Recommended wording:

> Follow the rules in [`01-java-language-modernisation.md`](./01-java-language-modernisation.md) and schedule the work according to [`05-migration-phases.md`](./05-migration-phases.md).

---

## Recommended rollout summary

### Wave A — low-risk pilots

- `tools`
- `test`
- `liquibase`
- `test-suite`

Goal: prove formatting, review, and CI workflow with small changes.

### Wave B — broad syntax modernization

- `api`
- `web`

Goal: apply Priority 1 patterns broadly:

- pattern matching `instanceof`
- `var`
- switch expressions
- text blocks
- String API improvements

### Wave C — structural modernization

- `api` first, then `web` where relevant

Goal:

- records
- sealed classes
- collection factories
- Stream improvements

### Wave D — legacy API retirement

- `api`

Goal:

- `java.time` migration
- SecurityManager removal
- HTTP client redesign/replacement

---

## Final recommendations

1. Start with **small, mechanical wins** to build confidence and consistency.
2. Treat `java.time` as a **separate strategic migration**, not a cleanup.
3. Keep `records` and `sealed` focused on the right kinds of types; do not force them onto entities or extension points.
4. Remove `OpenmrsSecurityManager` before it becomes a compatibility blocker.
5. Keep every migration task linked to a GitHub issue and reviewed against this document.

## Documentation follow-ups

- This document should remain the reference for Java language modernization decisions.
- Phase sequencing should be documented in [`05-migration-phases.md`](./05-migration-phases.md).
- The main [`README.md`](../../README.md) should be reviewed separately for Java-version wording, because it still contains Java 8-era build guidance that can drift from the actual Java 21 baseline.
