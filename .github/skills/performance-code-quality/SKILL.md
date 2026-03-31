---
name: performance-code-quality
description: Performance awareness and code quality — SOLID design, function size, Big O reasoning, data structure selection, error handling discipline, parameter hygiene, and algorithmic hygiene
license: MIT
---

# Performance & Code Quality

Use this skill when writing, reviewing, or refactoring backend code. Apply these standards regardless of language.

## SOLID design principles

- **Single Responsibility** — each class, module, or function has one reason to change. This directly supports the size limits below.
- **Open / Closed** — extend behaviour through new subclasses, decorators, or plugin registrations rather than modifying existing code. Define abstract base classes or Protocols for extension points.
- **Liskov Substitution** — any subclass or Protocol implementor must be usable wherever its parent type is expected, without breaking callers. Don't override methods to raise `NotImplementedError` or silently change semantics.
- **Interface Segregation** — keep abstractions small and focused. Split broad interfaces into narrow ones so implementors are not forced to stub methods they don't need.
- **Dependency Inversion** — depend on abstractions (ABCs, Protocols, interfaces), not concrete classes. Use factory functions, constructor injection, or registries to wire implementations at runtime.

## File and function size

- **Functions**: max ~30 statements. If a function exceeds this, extract coherent blocks into well-named helpers.
- **Files / modules**: max ~300–400 lines. Split by responsibility when a file grows beyond this.
- **Classes**: favour small, single-responsibility classes. A class with more than ~10 public methods likely needs decomposition.
- **Nesting depth**: max 3 levels of indentation inside a function. Flatten with early returns, guard clauses, or extraction.

## Big O awareness

- Before writing a loop or collection operation, state the expected time and space complexity in your reasoning.
- Prefer O(1) lookups (hash maps / sets) over O(n) scans when checking membership or deduplicating.
- Avoid nested loops over the same collection (O(n²)) when a single-pass approach with an index/map exists.
- When sorting is needed, leverage built-in sorts (O(n log n)) rather than hand-rolling.
- For hot paths, measure before optimising — but **always** avoid accidentally quadratic code.

## Data structure selection

| Need | Prefer | Avoid |
|------|--------|-------|
| Key-based lookup | Hash map / dict | Linear scan of list |
| Uniqueness check | Set | List with `in` check |
| Ordered iteration + fast lookup | Sorted container or indexed dict | Repeated sorts |
| FIFO queue | `deque` / ring buffer | List with `pop(0)` / shift |
| Priority ordering | Heap / priority queue | Repeated min/max scans |
| Append-heavy sequence | Dynamic array (list) | Linked list (cache-unfriendly) |

- Choose immutable structures (tuples, frozen sets, readonly arrays) when data does not change after creation — they signal intent and prevent accidental mutation.

## Algorithmic hygiene

- **Don't repeat work**: cache or memoise expensive computations that are called with the same inputs (e.g., `@functools.lru_cache`, module-level dicts).
- **Batch I/O**: prefer one bulk read/write over N individual calls (database queries, HTTP requests, file operations).
- **Lazy evaluation**: defer expensive computation until the result is actually needed; use generators or iterators instead of materialising full collections.
- **Short-circuit evaluation**: order boolean conditions so the cheapest or most likely-to-fail check runs first.
- **Avoid string concatenation in loops**: build a list and join once, or use a string builder / buffer.

## Error handling discipline

- **Never use bare catch-all handlers** (`except:`, `catch { }` with no type). Always catch specific exception types.
- **Avoid overly broad handlers** (`except Exception`, `catch (Exception e)`) unless at a top-level boundary (CLI entry point, HTTP handler). Within internal code, catch the narrowest type possible.
- **Don't swallow errors silently** — every catch block must either re-raise, log with context, or add the error to a structured result. A bare `pass` / empty catch body is never acceptable.
- **Pick one error strategy per layer and stick to it**:
  - *Internal / library code* — raise exceptions; let callers decide.
  - *Boundary / orchestration code* — catch, collect into a result object, and report.
  - *Never mix* returning `None` for errors in some functions and raising in others at the same abstraction level.
- **Include context in error messages** — mention what was being attempted, which input caused the failure, and what the caller should do.

## Parameter and function signature hygiene

- **Max ~4 parameters per function**. When a function needs more, group related parameters into a configuration object or data class.
- **Avoid boolean flag parameters** that fork a function into two different behaviours. Instead, split into two clearly-named functions or use a strategy/enum parameter.
- **Don't use strings where enums belong** — if a parameter selects between a known set of options (e.g., client type, compilation strategy, host provider), define an enum or constant set and dispatch on it. String comparison is fragile, typo-prone, and invisible to static analysis.

## Separation of concerns

- A single function should operate at **one level of abstraction**. Don't mix I/O, business logic, and output formatting in the same function body.
- **Orchestration functions** call other functions in sequence but contain no domain logic themselves.
- **Worker functions** perform a single cohesive task and return a result; they don't decide what to do next.
- If a function validates input, resolves dependencies, performs file I/O, and formats output, split it into those four steps invoked by a thin orchestrator.

## Refactoring triggers

When reviewing or modifying code, actively refactor if you spot:

1. **Duplicated logic** — extract into a shared function.
2. **God function** — break into smaller, testable units.
3. **Primitive obsession** — replace raw dicts/tuples with named data classes or typed objects.
4. **Deep nesting** — flatten with guard clauses or strategy extraction.
5. **Magic numbers / strings** — extract to named constants.
6. **Dead code** — remove unused functions, imports, and variables.
7. **Bare / broad exception handlers** — narrow the catch type or add context and logging.
8. **God file** — split files exceeding ~400 lines by responsibility (e.g., one file per command group, one class per module).
9. **Long parameter lists** — group into a config/options object.
10. **Boolean flag forking** — split into separate functions or replace with enum/strategy.
11. **String-typed dispatch** — replace string comparisons with enums or typed registries.
12. **Mixed abstraction levels** — extract I/O, logic, and formatting into their own layers.

## Review checklist

When reviewing a diff, verify:

- [ ] No function exceeds ~30 statements.
- [ ] No file exceeds ~400 lines.
- [ ] Loops and collection operations have justifiable complexity.
- [ ] Data structures match access patterns (map for lookup, set for membership, etc.).
- [ ] No accidental O(n²) from nested iterations or repeated lookups.
- [ ] Expensive operations are batched or cached where appropriate.
- [ ] New classes follow single responsibility; abstractions are narrow and substitutable.
- [ ] Dependencies point toward abstractions, not concrete implementations.
- [ ] No bare or overly broad exception handlers; every catch block logs or re-raises with context.
- [ ] Error strategy is consistent within the same abstraction layer (all raise, or all return result objects).
- [ ] Functions have ≤4 parameters; config objects used for larger signatures.
- [ ] No boolean flags that fork a function into unrelated behaviours.
- [ ] Dispatch on known option sets uses enums or typed constants, not raw strings.
- [ ] Each function operates at a single level of abstraction (no mixed I/O + logic + formatting).
