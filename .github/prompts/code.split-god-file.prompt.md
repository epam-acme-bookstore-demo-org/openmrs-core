---
description: Break a large file into smaller modules while preserving the public API
argument-hint: "Path to the oversized file to split"
---

# Split God File

Decompose an oversized file into well-scoped modules without breaking callers.

## Target File

${input:file}

## Instructions

1. Read the target file fully.
2. Identify logical responsibility groups (e.g., by class, command group, feature area, or abstraction layer).
3. For each group, propose a new file path and list the symbols (functions, classes, constants) that would move there.
4. Identify the public API — symbols imported or called from outside this file. These must remain importable from the original module (re-export via `__init__.py` or equivalent).
5. Present the split plan as a table:

   | New module | Responsibility | Symbols moved | Lines |
   |------------|---------------|---------------|-------|

6. Ask for confirmation before proceeding.
7. Apply the split:
   - Create the new files with the moved code.
   - Update the original file to re-export public symbols.
   - Update all internal imports across the codebase.
8. Run linting and tests to verify nothing broke.
