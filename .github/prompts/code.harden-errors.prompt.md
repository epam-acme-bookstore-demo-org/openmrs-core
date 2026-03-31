---
description: Scan for bare and overly broad exception handlers and fix them with proper types and context
argument-hint: "File path or folder to scan for error handling issues"
---

# Harden Error Handling

Find and fix unsafe exception handling patterns across the target area.

## Target

${input:target}

## Instructions

1. Load the **performance-code-quality** skill (error handling discipline section).
2. Search the target for these patterns:
   - **Bare catch-all**: `except:`, `catch { }` with no type — severity: critical.
   - **Broad handler**: `except Exception`, `catch (Exception e)` in non-boundary code — severity: high.
   - **Silent swallowing**: catch block with only `pass`, empty body, or no logging — severity: high.
   - **Inconsistent strategy**: same module mixes raising exceptions with returning `None` for errors — severity: moderate.
   - **Context-free messages**: `raise ValueError("error")` without explaining what input failed — severity: low.
3. For each finding, report:
   - **File and line**
   - **Pattern matched**
   - **Severity**
   - **Suggested fix** — the specific exception type to catch, the context to add, or the restructuring needed.
4. Group findings by file and sort by severity.
5. Ask for confirmation, then apply fixes file by file, running tests after each file is updated.
