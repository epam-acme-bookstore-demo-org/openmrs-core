---
name: testcontainers-standards
description: Testcontainers conventions — container lifecycle, configuration, test patterns, and CI integration for real infrastructure dependencies
license: MIT
---

# Testcontainers Standards

Use this skill when writing integration tests that use Testcontainers for real dependency containers.

## Purpose

Use Testcontainers to run integration tests against real dependency containers (databases, message brokers, caches, search engines) instead of mocks or in-memory substitutes. This ensures high-fidelity tests that catch issues mocking would miss.

## Container Lifecycle

### Scoping
- **Per-suite containers** (preferred): Start containers once in a `beforeAll` / global setup, share across tests in the suite—balances speed with isolation.
- **Per-test containers**: Only when tests mutate container state in ways that cannot be reset between runs.

### Startup & Readiness
- Always use the `withWaitStrategy()` or equivalent readiness check so tests do not start before the container is ready.
- Prefer `Wait.forHealthCheck()` or `Wait.forLogMessage()` over fixed delays.
- Set explicit startup timeouts to fail fast rather than hang.

### Cleanup
- Rely on Testcontainers' built-in Ryuk reaper for automatic cleanup.
- Call `container.stop()` in `afterAll` as a best practice, but do not depend solely on it—CI runners may kill processes before teardown completes.

## Container Configuration

### Images
- Pin image tags to a specific version (e.g., `postgres:16.3-alpine`), never use `latest`.
- Prefer `-alpine` or slim variants to reduce pull time and disk usage.

### Networking
- Use mapped ports (`container.getMappedPort(5432)`) rather than fixed host ports to avoid conflicts in parallel CI.
- When multiple containers need to communicate, create an explicit Docker network via `new Network()` and attach containers to it.

### Environment & Volumes
- Pass configuration through environment variables, not mounted config files, to keep tests self-contained.
- Use `withCopyFilesToContainer()` or `withCopyContentToContainer()` for seed data files when needed.

## Test Patterns

### Connection Strings
- Build connection strings dynamically from `container.getHost()` and `container.getMappedPort()`.
- Inject them into your application config or DI container during test setup.

### Database Tests
- Run migrations against the container before tests.
- Use transactions that roll back after each test, or truncate tables in `beforeEach`, to keep tests independent.

### Message Broker / Queue Tests
- Create unique topic/queue names per test run to avoid cross-run interference.
- Drain consumers fully before asserting to avoid flaky ordering issues.

## CI Integration

### Docker-in-Docker
- GitHub Actions: Use the default runner environment—Docker is available out of the box.
- Ensure the CI runner has sufficient memory and disk for the containers you declare.

### Caching
- Cache pulled images using CI layer caching or a container registry mirror to speed up pipeline runs.

### Parallel Jobs
- Testcontainers handles port allocation automatically, so parallel jobs on the same runner are safe when using mapped ports.

## Anti-Patterns

| Avoid | Do Instead |
|-------|-----------|
| Fixed host ports (`5432:5432`) | Use mapped ports |
| `sleep(5000)` for readiness | Use wait strategies |
| `latest` image tags | Pin to a specific version |
| Long-lived shared containers across suites | Scope containers to the test suite |
| Ignoring container logs on failure | Capture and attach logs in test reports |
