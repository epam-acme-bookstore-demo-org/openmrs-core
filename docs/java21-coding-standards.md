# Java 21 Coding Standards for OpenMRS Core Modernisation

Work item: #24

## 1. Introduction

This document defines the Java 21 coding standards for OpenMRS Core as the project moves from Java 8-era patterns to modern Java language and library features.

These standards apply to:

- all new production code in `api`, `web`, and `webapp`
- refactoring work done as part of the Java 21 modernisation
- opportunistic clean-up in files already being changed for other work

These standards do **not** require large-scale rewrites of stable code that is unrelated to the current change. Apply them when:

- adding new classes or methods
- modifying existing logic
- replacing legacy constructs with low-risk modern equivalents
- performing targeted refactors identified in the modernisation plan

General principles:

- prefer changes that improve readability, correctness, and maintainability
- favour small, mechanical modernisations before deeper design changes
- keep code compatible with the existing OpenMRS architecture, Spring 7, Hibernate 7, and Jakarta EE APIs
- follow existing repository conventions and `checkstyle.xml`
- do not introduce a Java 21 feature only because it is available; use it when it makes the code clearer

Relevant JEPs used throughout this document:

- [JEP 286: Local-Variable Type Inference](https://openjdk.org/jeps/286)
- [JEP 378: Text Blocks](https://openjdk.org/jeps/378)
- [JEP 361: Switch Expressions](https://openjdk.org/jeps/361)
- [JEP 394: Pattern Matching for `instanceof`](https://openjdk.org/jeps/394)
- [JEP 395: Records](https://openjdk.org/jeps/395)
- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)
- [JEP 358: Helpful NullPointerExceptions](https://openjdk.org/jeps/358)
- [JEP 269: Convenience Factory Methods for Collections](https://openjdk.org/jeps/269)
- [JEP 213: Milling Project Coin](https://openjdk.org/jeps/213)

## 2. Local Variable Type Inference (`var`)

`var` reduces noise when the type is obvious from the initializer. In OpenMRS Core, use it to remove repetition, not to hide meaning.

Relevant JEP:

- [JEP 286: Local-Variable Type Inference](https://openjdk.org/jeps/286)

### Apply when

- the initializer makes the type obvious
- the variable has a short scope
- the declared type would repeat the right-hand side
- the exact concrete type matters less than the variable name

### Do not apply when

- the initializer is ambiguous
- the variable represents an important domain concept and the explicit type improves readability
- the type is part of the business meaning, API contract, or review discussion
- using `null` as the initializer

### Before

```java
Patient patient = patientService.getPatientByUuid(patientUuid);
List<Encounter> encounters = encounterService.getEncountersByPatient(patient);
Map<String, Concept> conceptsByMapping = conceptService.getConceptMappings(source);
```

### After

```java
var patient = patientService.getPatientByUuid(patientUuid);
var encounters = encounterService.getEncountersByPatient(patient);
var conceptsByMapping = conceptService.getConceptMappings(source);
```

### Before

```java
Order order = orderService.getOrder(orderId);
BaseOpenmrsObject owner = order.getCreator();
```

### After

```java
var order = orderService.getOrder(orderId);
BaseOpenmrsObject owner = order.getCreator();
```

### Rules

- Use `var` for local variables only, never for fields, method parameters, or return types.
- Prefer `var` when the right-hand side is `new`, a fluent builder call, or a clearly named factory method.
- Keep explicit types when they improve understanding more than they add noise.
- Do not use `var` for primitives when the exact numeric type is easy to miss.

## 3. Text Blocks

Text blocks make multi-line strings easier to read and maintain. In OpenMRS Core, use them for SQL, HQL, HTML fragments, JSON payloads, XML snippets, and large user-facing messages.

Relevant JEP:

- [JEP 378: Text Blocks](https://openjdk.org/jeps/378)

### Apply when

- the string spans multiple lines
- preserving formatting matters
- the string contains many quotes, concatenations, or newline escapes

### Do not apply when

- the string is a single short line
- leading whitespace would be confusing
- formatting must be assembled dynamically from many conditional fragments

### Before

```java
String sql = "select p.patient_id, p.uuid, pn.given_name, pn.family_name " +
        "from patient p " +
        "join person_name pn on pn.person_id = p.patient_id and pn.voided = false " +
        "where p.voided = false " +
        "and pn.given_name like :name " +
        "order by pn.family_name, pn.given_name";
```

### After

```java
String sql = """
        select p.patient_id, p.uuid, pn.given_name, pn.family_name
        from patient p
        join person_name pn on pn.person_id = p.patient_id and pn.voided = false
        where p.voided = false
          and pn.given_name like :name
        order by pn.family_name, pn.given_name
        """;
```

### Before

```java
String payload = "{\n" +
        "  \"patientUuid\": \"" + patient.getUuid() + "\",\n" +
        "  \"encounterType\": \"" + encounter.getEncounterType().getName() + "\"\n" +
        "}";
```

### After

```java
String payload = """
        {
          "patientUuid": "%s",
          "encounterType": "%s"
        }
        """.formatted(patient.getUuid(), encounter.getEncounterType().getName());
```

### Rules

- Prefer text blocks for SQL, JSON, XML, HTML, and test fixtures.
- Keep indentation consistent with surrounding code.
- Use `.formatted(...)` or templating helpers instead of manual concatenation.
- Avoid text blocks for values that are truly single-line.

## 4. Switch Expressions

Switch expressions reduce boilerplate and make intent explicit by returning a value directly. They are preferred over traditional `switch` statements when selecting a result.

Relevant JEP:

- [JEP 361: Switch Expressions](https://openjdk.org/jeps/361)

### Apply when

- each branch computes or returns a value
- the old switch used temporary variables plus `break`
- refactoring string dispatch to enum-based or sealed-type dispatch

### Do not apply when

- branch logic is long and deserves extraction to named methods
- the switch is better replaced with polymorphism or a strategy
- the input is stringly typed and should first become an enum

### Before

```java
String auditAction;
switch (order.getAction()) {
    case ORDER:
        auditAction = "created";
        break;
    case REVISE:
        auditAction = "revised";
        break;
    case DISCONTINUE:
        auditAction = "discontinued";
        break;
    default:
        throw new IllegalArgumentException("Unsupported order action: " + order.getAction());
}
```

### After

```java
String auditAction = switch (order.getAction()) {
    case ORDER -> "created";
    case REVISE -> "revised";
    case DISCONTINUE -> "discontinued";
};
```

### Before

```java
String message;
switch (requestContext) {
    case "patient":
        message = "Load patient dashboard";
        break;
    case "encounter":
        message = "Load encounter summary";
        break;
    default:
        message = "Load home page";
}
```

### After

```java
String message = switch (requestContext) {
    case "patient" -> "Load patient dashboard";
    case "encounter" -> "Load encounter summary";
    default -> "Load home page";
};
```

### Pattern matching with `case` labels

Pattern matching in `switch` is useful when branching on a small, known type hierarchy, especially together with sealed interfaces.

```java
String describe(ClinicalEvent event) {
    return switch (event) {
        case PatientEvent patientEvent -> "Patient " + patientEvent.patient().getUuid();
        case EncounterEvent encounterEvent -> "Encounter " + encounterEvent.encounter().getUuid();
        case OrderEvent orderEvent -> "Order " + orderEvent.order().getUuid();
    };
}
```

### Rules

- Prefer switch expressions when calculating a value.
- Use arrow labels (`->`) for simple branches.
- Use exhaustive switches for enums and sealed hierarchies.
- Throw an exception in `default` only when the input is truly invalid.

## 5. Pattern Matching for `instanceof`

Pattern matching for `instanceof` removes the repeated cast that commonly appears in Java 8 code.

Relevant JEP:

- [JEP 394: Pattern Matching for `instanceof`](https://openjdk.org/jeps/394)

### Apply when

- code checks a type and immediately casts it
- the scoped variable name improves readability

### Do not apply when

- multiple unrelated type checks indicate missing polymorphism
- the branch is large enough that extraction is clearer

### Before

```java
if (order instanceof DrugOrder) {
    DrugOrder drugOrder = (DrugOrder) order;
    return drugOrder.getDrug().getName();
}
```

### After

```java
if (order instanceof DrugOrder drugOrder) {
    return drugOrder.getDrug().getName();
}
```

### Before

```java
if (obs.getValueCoded() instanceof Concept) {
    Concept concept = (Concept) obs.getValueCoded();
    return concept.getDisplayString();
}
```

### After

```java
if (obs.getValueCoded() instanceof Concept concept) {
    return concept.getDisplayString();
}
```

### Rules

- Use pattern matching when it eliminates an immediate cast.
- Name the pattern variable after the domain concept, not the type suffix alone.
- If the same type check appears in many places, prefer polymorphic methods.

## 6. Records

Records are ideal for immutable data carriers. In OpenMRS Core, use them for DTOs, projection results, event payloads, configuration snapshots, and value objects that mainly carry data.

Relevant JEP:

- [JEP 395: Records](https://openjdk.org/jeps/395)

### Apply when

- the type is immutable by design
- equality should be value-based
- the class primarily contains fields, accessors, validation, and derived helpers
- the type is used as a projection from JPA, JDBC, or service boundaries

### Do not apply when

- the type is a JPA or Hibernate entity
- the type requires mutable state
- the class has complex lifecycle behaviour, inheritance needs, or framework proxy requirements

### Before

```java
public final class PatientSearchResult {

    private final Integer patientId;
    private final String uuid;
    private final String givenName;
    private final String familyName;

    public PatientSearchResult(Integer patientId, String uuid, String givenName, String familyName) {
        this.patientId = patientId;
        this.uuid = uuid;
        this.givenName = givenName;
        this.familyName = familyName;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public String getUuid() {
        return uuid;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }
}
```

### After

```java
public record PatientSearchResult(
        Integer patientId,
        String uuid,
        String givenName,
        String familyName) {
}
```

### Before

```java
public final class ConceptMappingKey {

    private final String source;
    private final String code;

    public ConceptMappingKey(String source, String code) {
        this.source = source;
        this.code = code;
    }

    public String getSource() {
        return source;
    }

    public String getCode() {
        return code;
    }
}
```

### After

```java
public record ConceptMappingKey(String source, String code) {

    public ConceptMappingKey {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
```

### Rules

- Prefer records for immutable DTOs and value objects.
- Do not convert entities such as `Patient`, `Encounter`, `Order`, or `Concept` to records.
- Keep record bodies small; if behaviour becomes dominant, use a regular class.
- Use canonical constructors for invariants and normalization.

## 7. Sealed Classes and Interfaces

Sealed types explicitly control which implementations are allowed. Use them for closed hierarchies where OpenMRS knows all valid variants at compile time.

Relevant JEP:

- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)

### Apply when

- the hierarchy is intentionally closed
- exhaustive `switch` handling is desirable
- the subtype list is small, stable, and part of the domain model

### Do not apply when

- third-party modules must be able to extend the type
- the hierarchy is part of a public extension point
- framework proxies or dynamic subclassing are required

### Before

```java
public interface ClinicalEvent {
}

public final class PatientEvent implements ClinicalEvent {
    private final Patient patient;
}

public final class EncounterEvent implements ClinicalEvent {
    private final Encounter encounter;
}

public final class OrderEvent implements ClinicalEvent {
    private final Order order;
}
```

### After

```java
public sealed interface ClinicalEvent permits PatientEvent, EncounterEvent, OrderEvent {
}

public record PatientEvent(Patient patient) implements ClinicalEvent {
}

public record EncounterEvent(Encounter encounter) implements ClinicalEvent {
}

public record OrderEvent(Order order) implements ClinicalEvent {
}
```

### Before

```java
abstract class IdentifierValidationResult {
}

final class ValidIdentifier extends IdentifierValidationResult {
}

final class DuplicateIdentifier extends IdentifierValidationResult {
}

final class InvalidFormat extends IdentifierValidationResult {
}
```

### After

```java
sealed interface IdentifierValidationResult
        permits ValidIdentifier, DuplicateIdentifier, InvalidFormat {
}

record ValidIdentifier() implements IdentifierValidationResult {
}

record DuplicateIdentifier(String identifier) implements IdentifierValidationResult {
}

record InvalidFormat(String identifier, String reason) implements IdentifierValidationResult {
}
```

### Rules

- Use sealed types for closed internal hierarchies, not for extension APIs.
- Combine sealed types with records and switch expressions when appropriate.
- Keep the permitted subtype set near the sealed declaration.

## 8. Enhanced `NullPointerException` Messages

Java 21 provides detailed `NullPointerException` messages that identify which part of an expression was `null`.

Relevant JEP:

- [JEP 358: Helpful NullPointerExceptions](https://openjdk.org/jeps/358)

### JVM flag

Detailed messages are enabled by default in Java 21. They can be controlled with:

```bash
-XX:+ShowCodeDetailsInExceptionMessages
```

### Benefits

- faster diagnosis in logs and CI failures
- less need to reproduce simple null dereferences locally
- better observability for legacy paths still being hardened

### Before

```java
String conceptName = encounter.getVisit().getPatient().getPersonName().getFullName();
```

Typical legacy failure:

```text
java.lang.NullPointerException
```

### After

```java
String conceptName = encounter.getVisit().getPatient().getPersonName().getFullName();
```

Typical Java 21 failure:

```text
Cannot invoke "org.openmrs.PersonName.getFullName()" because the return value of
"org.openmrs.Patient.getPersonName()" is null
```

### Rules

- Keep helpful NPEs enabled in all local, CI, and runtime environments unless there is a verified operational reason to disable them.
- Do not rely on enhanced messages as a substitute for input validation.
- Prefer failing fast with clear validation for expected null scenarios.

## 9. `java.time` API

Use `java.time` for new code. Legacy `Date` and `Calendar` APIs are mutable, error-prone, and less expressive.

There is no JEP for `java.time`; it originates from JSR-310 and is part of the modern Java standard library.

### Apply when

- writing new code
- touching existing code that converts, formats, compares, or stores dates and times
- defining APIs across module boundaries

### Do not apply when

- a framework API still requires `Date`; convert at the boundary
- changing the type would create an unsafe persistence migration in the same PR

### Mapping table

| Legacy type | Preferred replacement | Use for |
|---|---|---|
| `java.util.Date` | `Instant` | timestamp in UTC |
| `java.sql.Timestamp` | `Instant` or `OffsetDateTime` | database timestamps |
| `java.util.Calendar` | `ZonedDateTime` | timezone-aware calculations |
| `java.sql.Date` | `LocalDate` | date without time |
| `java.sql.Time` | `LocalTime` | time without date |
| custom start/end date pairs | `LocalDate`, `Instant`, or `Duration`/`Period` | domain-specific ranges |

### Before

```java
Date now = new Date();
Calendar calendar = Calendar.getInstance();
calendar.setTime(now);
calendar.add(Calendar.DAY_OF_MONTH, 7);
Date nextReviewDate = calendar.getTime();
```

### After

```java
Instant now = Instant.now();
LocalDate nextReviewDate = LocalDate.now(ZoneOffset.UTC).plusDays(7);
```

### Before

```java
Date encounterDate = encounter.getEncounterDatetime();
boolean sameDay = DateUtils.isSameDay(encounterDate, new Date());
```

### After

```java
LocalDate encounterDate = encounter.getEncounterDatetime()
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate();
boolean sameDay = encounterDate.equals(LocalDate.now(ZoneId.systemDefault()));
```

### Rules

- Use `Instant` for machine timestamps.
- Use `LocalDate` for clinical dates without time.
- Use `ZonedDateTime` only when a zone is part of the business meaning.
- Convert legacy `Date` at framework boundaries instead of propagating it deeper into new code.

## 10. Stream API Enhancements

Java 21 includes a mature Stream API with features that simplify common collection transformations.

### Apply when

- the stream pipeline remains readable
- the transformation is declarative and side-effect free
- the operation is naturally expressed as map/filter/collect

### Do not apply when

- a simple `for` loop is clearer
- the pipeline becomes hard to debug
- side effects dominate the operation

### `toList()`

#### Before

```java
List<String> identifiers = patients.stream()
        .map(Patient::getUuid)
        .collect(Collectors.toList());
```

#### After

```java
List<String> identifiers = patients.stream()
        .map(Patient::getUuid)
        .toList();
```

### `mapMulti()`

Use `mapMulti()` when one input element may emit zero, one, or many outputs without creating nested temporary streams.

#### Before

```java
List<ConceptAnswer> answers = concepts.stream()
        .flatMap(concept -> concept.getAnswers().stream())
        .toList();
```

#### After

```java
List<ConceptAnswer> answers = concepts.stream()
        .<ConceptAnswer>mapMulti((concept, downstream) -> {
            for (ConceptAnswer answer : concept.getAnswers()) {
                downstream.accept(answer);
            }
        })
        .toList();
```

### `teeing()`

Use `teeing()` when one traversal should produce two aggregate results.

#### Before

```java
long activeCount = orders.stream().filter(order -> !order.getVoided()).count();
long discontinuedCount = orders.stream().filter(Order::getVoided).count();
OrderSummary summary = new OrderSummary(activeCount, discontinuedCount);
```

#### After

```java
OrderSummary summary = orders.stream().collect(Collectors.teeing(
        Collectors.filtering(order -> !order.getVoided(), Collectors.counting()),
        Collectors.filtering(Order::getVoided, Collectors.counting()),
        OrderSummary::new));
```

### Rules

- Prefer `.toList()` over `Collectors.toList()` when mutability is not required.
- Use `mapMulti()` for performance-sensitive flattening or conditional expansion.
- Use `teeing()` when it avoids multiple passes and remains understandable.
- Do not force streams into hot paths where loops are clearer and faster.

## 11. String API Enhancements

Modern `String` methods remove common utility boilerplate and improve Unicode handling.

### Apply when

- replacing manual trim-and-empty checks
- processing multi-line content
- formatting readable templates

### Do not apply when

- using a library type that already encapsulates the operation better
- a regular expression or parser is the correct solution

### Before

```java
if (patientIdentifier == null || patientIdentifier.trim().isEmpty()) {
    throw new IllegalArgumentException("identifier must not be blank");
}

String normalized = locationName.trim();
List<String> lines = Arrays.asList(message.split("\\R"));
String html = String.format("<span class=\"tag\">%s</span>", concept.getDisplayString());
```

### After

```java
if (patientIdentifier == null || patientIdentifier.isBlank()) {
    throw new IllegalArgumentException("identifier must not be blank");
}

String normalized = locationName.strip();
List<String> lines = message.lines().toList();
String html = "<span class=\"tag\">%s</span>".formatted(concept.getDisplayString());
```

### `indent()`

```java
String debugMessage = """
        Patient import failed
        Identifier: %s
        Source: %s
        """.formatted(identifier, sourceName).indent(2);
```

### Rules

- Prefer `isBlank()` over `trim().isEmpty()`.
- Prefer `strip()` over `trim()` for Unicode-aware whitespace handling.
- Use `lines()` instead of splitting on line-break regexes.
- Use `.formatted(...)` for readable inline formatting.

## 12. Collection Factory Methods

Collection factory methods create small immutable collections with less ceremony.

Relevant JEP:

- [JEP 269: Convenience Factory Methods for Collections](https://openjdk.org/jeps/269)

### Apply when

- creating fixed configuration values
- returning small immutable collections
- defining test fixtures or lookup tables

### Do not apply when

- the collection must be mutated later
- the collection contains `null` values
- the collection is large enough that readability suffers

### Before

```java
List<String> supportedOrderTypes = Arrays.asList("drug", "test", "referral");

Map<String, String> attributes = new HashMap<>();
attributes.put("source", "registration");
attributes.put("status", "pending");
```

### After

```java
List<String> supportedOrderTypes = List.of("drug", "test", "referral");

Map<String, String> attributes = Map.of(
        "source", "registration",
        "status", "pending");
```

### Before

```java
Set<EncounterRole> requiredRoles = new HashSet<>();
requiredRoles.add(clinicianRole);
requiredRoles.add(recorderRole);
```

### After

```java
Set<EncounterRole> requiredRoles = Set.of(clinicianRole, recorderRole);
```

### Rules

- Prefer `List.of`, `Set.of`, and `Map.of` for small immutable collections.
- Remember that these factories reject `null`.
- If mutability is needed, wrap explicitly: `new ArrayList<>(List.of(...))`.

## 13. Try-with-resources Improvements

Java allows effectively final variables to be used directly in try-with-resources, reducing duplication.

Relevant JEP:

- [JEP 213: Milling Project Coin](https://openjdk.org/jeps/213)

### Apply when

- the resource variable is already declared and effectively final
- the refactor removes redundant variable reassignment

### Do not apply when

- the variable is reassigned
- resource lifetime is intentionally larger than the try block

### Before

```java
BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
try (BufferedReader closeableReader = reader) {
    return closeableReader.lines().toList();
}
```

### After

```java
BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
try (reader) {
    return reader.lines().toList();
}
```

### Before

```java
InputStream stream = moduleClassLoader.getResourceAsStream("metadata/orders.json");
try (InputStream input = stream) {
    return objectMapper.readValue(input, OrderMetadata.class);
}
```

### After

```java
InputStream stream = moduleClassLoader.getResourceAsStream("metadata/orders.json");
try (stream) {
    return objectMapper.readValue(stream, OrderMetadata.class);
}
```

### Rules

- Use effectively final resources directly in try-with-resources.
- Keep the resource declaration close to the try block.
- Prefer higher-level APIs when a manual stream is not needed.

## 14. Exception Handling Standards

OpenMRS Core currently has many broad catches and silent catch blocks. New and refactored code must use specific exception handling with clear diagnostics.

### Apply when

- handling a specific, expected failure
- adding context before rethrowing
- translating between architectural layers

### Do not apply when

- catching `Exception` only to log and continue
- swallowing exceptions silently
- using exceptions for normal control flow

### Before

```java
try {
    patientService.savePatient(patient);
}
catch (Exception e) {
    // ignore for now
}
```

### After

```java
try {
    patientService.savePatient(patient);
}
catch (ValidationException e) {
    throw new APIException("Failed to save patient " + patient.getUuid() + ": validation error", e);
}
catch (DataIntegrityViolationException e) {
    throw new APIException("Failed to save patient " + patient.getUuid() + ": data integrity violation", e);
}
```

### Before

```java
try {
    conceptService.saveConcept(concept);
}
catch (Exception e) {
    log.error("Save failed", e);
    throw e;
}
```

### After

```java
try {
    conceptService.saveConcept(concept);
}
catch (DuplicateConceptNameException e) {
    log.warn("Rejecting concept {} because the name already exists", concept.getUuid(), e);
    throw e;
}
catch (DataAccessException e) {
    throw new APIException("Unable to save concept " + concept.getUuid(), e);
}
```

### Rules

- Catch the narrowest exception type that matches the recovery or translation logic.
- Never use empty catch blocks or comment-only catch blocks.
- Log at the boundary where the exception is handled or translated, not at every layer.
- Include domain context in error messages: patient UUID, encounter UUID, concept code, order number, or request input.
- Use `warn` for expected-but-important business failures, `error` for unexpected failures.
- Re-throw with cause preservation.
- `catch (Exception)` requires explicit justification and should be rare.
- `catch (Throwable)` is prohibited except in top-level platform safety boundaries.

## 15. Method Design

Method signatures strongly affect maintainability. New and refactored code should prefer smaller, intention-revealing APIs.

### Standards

- maximum of **4 parameters** for new or significantly refactored methods
- avoid boolean flag parameters
- prefer domain objects, option objects, builders, or enums over long primitive parameter lists

Note: current `checkstyle.xml` allows up to 5 parameters. This standard is intentionally stricter for modernised code.

### Apply when

- introducing or refactoring public APIs
- methods have multiple optional or related inputs
- a boolean changes behaviour

### Do not apply when

- a framework callback signature is fixed
- changing a public API would create unsafe breakage without a migration plan

### Before

```java
public List<Encounter> findEncounters(
        Patient patient,
        Location location,
        Date fromDate,
        Date toDate,
        boolean includeVoided,
        boolean includeInactiveVisits) {
    // ...
}
```

### After

```java
public List<Encounter> findEncounters(EncounterSearchCriteria criteria) {
    // ...
}

public record EncounterSearchCriteria(
        Patient patient,
        Location location,
        LocalDate fromDate,
        LocalDate toDate,
        VoidHandling voidHandling,
        VisitState visitState) {
}
```

### Before

```java
public Order saveOrder(Order order, boolean validate, boolean audit) {
    // ...
}
```

### After

```java
public Order saveValidatedOrder(Order order) {
    // ...
}

public Order saveOrderWithoutAudit(Order order) {
    // ...
}
```

### Builder pattern example

```java
EncounterSearchCriteria criteria = new EncounterSearchCriteriaBuilder()
        .patient(patient)
        .location(location)
        .fromDate(LocalDate.now().minusDays(30))
        .toDate(LocalDate.now())
        .voidHandling(VoidHandling.EXCLUDE)
        .visitState(VisitState.ACTIVE_ONLY)
        .build();
```

### Rules

- Split methods when boolean flags select different behaviours.
- Group related parameters into a record or dedicated criteria object.
- Use builders when an object has many optional settings.
- Prefer enums over booleans when choosing modes.

## 16. Migration Priority

Modernisation should be applied in an order that maximizes safety and reviewability.

### Priority 1: Safe, mechanical changes

Apply first because they are low risk and easy to review:

- `instanceof` pattern matching
- switch expressions where behaviour is unchanged
- collection factory methods
- `String` API replacements such as `isBlank()` and `strip()`
- `.toList()` instead of `Collectors.toList()`
- try-with-resources improvements
- text blocks for existing multi-line literals

### Priority 2: Safe but broader API improvements

Apply next when touching a file or component:

- `java.time` at service and DTO boundaries
- increased `var` usage in obvious local contexts
- replacing temporary DTO classes with records
- replacing ad hoc result wrappers with records

### Priority 3: Design-oriented refactors

Apply deliberately, usually in dedicated PRs:

- removing boolean parameters
- reducing long parameter lists
- introducing sealed hierarchies
- replacing string dispatch with enums or sealed types
- decomposing god classes and large methods
- narrowing broad exception handling across a subsystem

### Rules

- Prefer one type of mechanical change per PR.
- Avoid mixing design changes with broad syntax modernisation unless tightly related.
- For legacy hot spots, modernise the most error-prone patterns first: exception handling, null-safety, and date/time handling.

## 17. OpenRewrite Integration

OpenRewrite should be used for repeatable, low-risk transformations, with manual review for semantics and design.

### Good candidates for automation

- `instanceof` + cast to pattern matching
- `Collectors.toList()` to `.toList()` where mutability is not required
- string concatenation to text blocks for known multi-line literals
- `trim().isEmpty()` or explicit blank checks to `isBlank()`
- collection factory method replacements where immutability is correct
- try-with-resources simplifications
- `Date` to `java.time` conversions at narrow boundaries with known semantics

### Require manual review

- introducing `var`
- converting classes to records
- introducing sealed hierarchies
- replacing string switches with enums
- refactoring long parameter lists
- removing boolean flags
- narrowing broad exception catches
- changing logging behaviour or error translation

### Recommended workflow

1. Run OpenRewrite on one module or recipe group at a time.
2. Review every diff for readability, mutability, and framework compatibility.
3. Run the normal validation pipeline:
   - `mvn verify`
   - `mvn checkstyle:check`
   - `mvn spotbugs:check`
   - `mvn spotless:check`
4. Separate automated refactors from manual design work when possible.

### Rules

- Treat OpenRewrite as an accelerator, not as a substitute for design review.
- Do not merge automated changes without human validation of behaviour.
- Prefer recipes that are deterministic, small in scope, and easy to roll back.
