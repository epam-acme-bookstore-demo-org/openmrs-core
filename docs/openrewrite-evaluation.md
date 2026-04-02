# OpenRewrite Evaluation for Java 21 Migration

## What is OpenRewrite?

[OpenRewrite](https://docs.openrewrite.org/) is an automated source code refactoring tool that applies
"recipes" — composable, pre-built transformation rules — to modernize codebases. It parses Java source
into a lossless semantic tree (LST), applies transformations, and writes back minimal diffs that preserve
formatting. This makes it ideal for large-scale migrations where manual changes would be error-prone and
time-consuming.

## Why We're Evaluating It

OpenMRS Core targets Java 21 (`maven.compiler.release=21`) but the codebase still uses Java 8 patterns
throughout its ~835 production Java files. Current analysis shows:

| Pattern                | Count | Opportunity                          |
|------------------------|-------|--------------------------------------|
| Record classes         | 0     | Introduce where appropriate          |
| Text blocks            | 0     | Replace multi-line string concats    |
| Switch expressions     | 0     | Modernize switch statements          |
| `var` usage            | 23    | Expand to obvious-type declarations  |
| Broad `catch` blocks   | 256   | Narrow to specific exception types   |

Manually modernizing all of these across the codebase would be slow and risky. OpenRewrite can automate
the safe, mechanical transformations while leaving nuanced changes for human review.

## Recipes Configured

### `org.openrewrite.java.migrate.UpgradeToJava21`

This is a **composite recipe** from the `rewrite-migrate-java` module that bundles dozens of
sub-recipes. Key transformations include:

| Category               | What It Does                                                      |
|------------------------|-------------------------------------------------------------------|
| **API migrations**     | Replaces deprecated/removed APIs with Java 21 equivalents         |
| **Language features**  | Converts to text blocks, switch expressions, pattern matching     |
| **Type inference**     | Expands `var` usage where types are obvious (local variables)     |
| **Collections**        | Migrates to `List.of()`, `Map.of()`, `Set.of()` factory methods   |
| **Streams/Lambdas**    | Modernizes anonymous classes to lambdas where safe                |
| **Deprecated removals**| Replaces `new Integer()`, `new Boolean()`, etc. with `.valueOf()` |
| **Security/Crypto**    | Updates deprecated security provider patterns                     |
| **I/O modernization**  | Migrates to `java.nio` APIs and try-with-resources                |

The full recipe list is available at:
https://docs.openrewrite.org/recipes/java/migrate/upgradetojava21

## Dry-Run Results

> **Note**: The dry-run could not be executed in the current environment because no Java runtime is
> installed on the build machine. The profile and plugin configuration have been verified structurally.

To execute the dry-run when Java 21 is available:

```bash
./mvnw -pl api rewrite:dryRun -Popenrewrite -B -ntp
```

This will generate a diff report at `api/target/rewrite/rewrite.patch` showing all proposed changes
without modifying any source files.

To run across all modules:

```bash
./mvnw rewrite:dryRun -Popenrewrite -B -ntp
```

### Expected Changes (Based on Codebase Analysis)

Based on the current codebase patterns, the dry-run is expected to propose:

- **Deprecated API replacements**: `new Integer()` → `Integer.valueOf()`, etc.
- **Text block conversions**: Multi-line SQL strings, HQL queries, XML snippets
- **Collection factory methods**: `Collections.unmodifiableList(Arrays.asList(...))` → `List.of(...)`
- **Try-with-resources**: Where `InputStream`/`OutputStream` are not already auto-closed
- **Lambda conversions**: Anonymous inner classes with single abstract methods
- **Type migration**: `javax.*` to `jakarta.*` where applicable
- **Redundant casts and type arguments**: Removal of unnecessary diamond operators, casts

## Recommendation

### Adopt (Safe, High Value)
- **Deprecated API replacements** — mechanical, low-risk, improves compiler warning count
- **Collection factory methods** — cleaner, immutable-by-default
- **Try-with-resources** — fixes actual resource leak risks
- **Diamond operator cleanup** — pure noise reduction

### Adopt with Review (Medium Risk)
- **Text block conversions** — review for correct whitespace/indentation
- **Lambda conversions** — verify serialization behavior is not affected
- **Switch expressions** — review for fall-through logic correctness

### Skip or Defer (Higher Risk)
- **Record class introductions** — changes class semantics (equals/hashCode), needs careful review
- **Pattern matching** — may change null-handling behavior in `instanceof` chains
- **`var` expansion** — style preference; defer until team agrees on coding standards

### Approach
1. Run dry-run on each module (`api`, `web`, `webapp`) to review proposed changes
2. Apply recipe-by-recipe rather than the full composite, starting with lowest-risk recipes
3. Run full test suite after each recipe application
4. Review diffs before committing — OpenRewrite is format-preserving but not infallible

## How to Run

### Dry-Run (Preview Only)
Shows proposed changes without modifying files:

```bash
# Single module
./mvnw -pl api rewrite:dryRun -Popenrewrite -B -ntp

# All modules
./mvnw rewrite:dryRun -Popenrewrite -B -ntp
```

The patch file is written to `<module>/target/rewrite/rewrite.patch`.

### Apply Changes
Modifies source files in place:

```bash
# Single module
./mvnw -pl api rewrite:run -Popenrewrite -B -ntp

# All modules
./mvnw rewrite:run -Popenrewrite -B -ntp
```

### Run Specific Recipes
To run a single sub-recipe instead of the full composite, modify the `<activeRecipes>` in `pom.xml`
or override via command line:

```bash
./mvnw -pl api rewrite:run -Popenrewrite \
  -Drewrite.activeRecipes=org.openrewrite.java.migrate.lang.UseTextBlocks \
  -B -ntp
```

## Risks and Considerations

### Formatting
- OpenRewrite preserves original formatting in most cases, but may conflict with the project's
  Spotless configuration. Run `./mvnw spotless:apply` after OpenRewrite changes.
- Verify with `./mvnw spotless:check` before committing.

### Test Impact
- Changes to `equals()`/`hashCode()` (e.g., record conversions) can break tests.
- Collection factory method migrations produce **immutable** collections — code that later
  mutates the collection will throw `UnsupportedOperationException` at runtime.
- Always run the full test suite after applying changes: `./mvnw verify -pl api`

### Build Reproducibility
- The OpenRewrite profile is opt-in (`-Popenrewrite`), so it does not affect normal builds.
- Pin plugin and recipe versions (currently `6.6.3` / `3.12.0`) to ensure reproducible results.

### Review Process
- Treat OpenRewrite output as machine-generated pull requests — always review diffs.
- Apply to one module at a time to keep PRs reviewable.
- Run dry-run first, review the patch, then apply.

## Plugin Configuration

The OpenRewrite plugin is configured as an opt-in Maven profile in the root `pom.xml`:

```xml
<profile>
  <id>openrewrite</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.openrewrite.maven</groupId>
        <artifactId>rewrite-maven-plugin</artifactId>
        <version>6.6.3</version>
        <configuration>
          <activeRecipes>
            <recipe>org.openrewrite.java.migrate.UpgradeToJava21</recipe>
          </activeRecipes>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-migrate-java</artifactId>
            <version>3.12.0</version>
          </dependency>
        </dependencies>
      </plugin>
    </plugins>
  </build>
</profile>
```

Activate with: `./mvnw <goal> -Popenrewrite`
