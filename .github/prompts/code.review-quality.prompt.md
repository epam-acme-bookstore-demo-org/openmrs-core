---
description: Run the full performance & code quality review checklist against a file or diff
argument-hint: "File path, folder, or 'staged changes' to review"
---

# Code Quality Review

Review code against the **performance-code-quality** standards and report every violation.

## Target

${input:target}

## Instructions

1. Load the **performance-code-quality** skill.
2. Read the target file(s) or diff.
3. Walk through **every item** in the review checklist:
   - Function size (max ~30 statements)
   - File size (max ~400 lines)
   - Loop / collection complexity (Big O)
   - Data structure fit (map, set, deque, heap)
   - Accidental O(n²)
   - Batching / caching of expensive operations
   - Single responsibility and narrow abstractions
   - Dependency direction (toward abstractions)
   - Bare / broad exception handlers
   - Consistent error strategy per layer
   - Parameter count (≤4) and config objects
   - Boolean flag forking
   - String-typed dispatch vs enums
   - Single abstraction level per function
4. For each violation found, report:
   - **File and location**
   - **Rule violated**
   - **What's wrong** (one sentence)
   - **Suggested fix** (concrete, actionable)
5. Summarise: total violations by severity (blocking / warning / nit) and top-3 priorities to address first.
