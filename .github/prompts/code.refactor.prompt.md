---
description: Identify and apply refactoring triggers on a file or module
argument-hint: "File path or module to refactor"
---

# Refactor Code

Scan a file or module for refactoring triggers and apply fixes.

## Target

${input:target}

## Instructions

1. Load the **performance-code-quality** skill.
2. Read the target file(s) thoroughly.
3. Check every refactoring trigger:
   - Duplicated logic
   - God functions (>30 statements)
   - Primitive obsession (raw dicts/tuples instead of typed objects)
   - Deep nesting (>3 levels)
   - Magic numbers / strings
   - Dead code (unused functions, imports, variables)
   - Bare / broad exception handlers
   - God files (>400 lines)
   - Long parameter lists (>4 params)
   - Boolean flag forking
   - String-typed dispatch
   - Mixed abstraction levels
4. For each trigger found, list it with file location and severity.
5. Propose a refactoring plan ordered by impact (most valuable first).
6. Ask for confirmation, then apply the refactors one by one, validating that existing tests still pass after each change.
