# Maven Dependency Caching in CI

## Purpose

Cache Maven dependencies in CI so jobs do not download the same artifacts on every run.
On this repository, a warm cache can save 2+ minutes per job, reduce load on Maven Central, and shorten feedback loops for build, test, and analysis workflows.

## Composite Action

Use the reusable composite action at [`../../.github/actions/setup-maven/action.yml`](../../.github/actions/setup-maven/action.yml).

### Inputs

- `java-version` — required Java version to install
- `distribution` — optional JDK distribution, default `temurin`

### What it does

The action handles JDK setup and Maven dependency caching in one step:

1. Installs the requested JDK with `actions/setup-java`
2. Caches `~/.m2/repository`
3. Keys the cache from the runner OS and the current `pom.xml` hash

### Cache key strategy

```yaml
key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
restore-keys: |
  ${{ runner.os }}-maven-
```

This keeps caches OS-specific, invalidates them when Maven dependencies change, and still allows partial cache hits.

## Usage Examples

### Single Java version job

```yaml
steps:
  - uses: actions/checkout@v6

  - name: Setup Maven
    uses: ./.github/actions/setup-maven
    with:
      java-version: '21'

  - name: Build
    run: ./mvnw verify -B -ntp
```

### Matrix build with multiple Java versions

```yaml
jobs:
  build:
    runs-on: ${{ matrix.platform }}
    strategy:
      matrix:
        platform: [ubuntu-latest, windows-latest]
        java-version: [21, 25]
    steps:
      - uses: actions/checkout@v6

      - name: Setup Maven
        uses: ./.github/actions/setup-maven
        with:
          java-version: ${{ matrix.java-version }}

      - name: Test
        run: mvn test --batch-mode --file pom.xml
```

### Current workflow references

- [`../../.github/workflows/ci-modernisation.yml`](../../.github/workflows/ci-modernisation.yml)
- [`../../.github/workflows/build.yaml`](../../.github/workflows/build.yaml)
- [`../../.github/workflows/codeql-analysis.yml`](../../.github/workflows/codeql-analysis.yml)
- [`../../.github/workflows/dependency-check.yml`](../../.github/workflows/dependency-check.yml)

## Best Practices

- Always use the composite action instead of duplicating `actions/setup-java` and `actions/cache` in each job.
- Include both `runner.os` and the `pom.xml` hash in the cache key.
- Keep `restore-keys` enabled so near matches can reuse older Maven caches.
- Cache only `~/.m2/repository`, not the entire `~/.m2` directory.
- Use the composite action when a job needs both JDK setup and Maven dependency caching.
- On Windows runners, keep the cache path as `~/.m2/repository`; `actions/cache` resolves it correctly.

## Anti-patterns

- Duplicating Maven cache configuration across jobs or workflows
- Using `actions/setup-java` with `cache: 'maven'` and a separate `actions/cache` step in the same job
- Caching `~/.m2` instead of `~/.m2/repository`
- Skipping `restore-keys` and forcing every near match to become a full cache miss

## Migration Guide

Replace inline JDK setup and Maven cache steps with the composite action.

### Before

```yaml
- name: Setup JDK
  uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: '21'

- name: Cache Maven dependencies
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-
```

### After

```yaml
- name: Setup Maven
  uses: ./.github/actions/setup-maven
  with:
    java-version: '21'
```

After switching, remove `cache: 'maven'` from any remaining `actions/setup-java` calls.
