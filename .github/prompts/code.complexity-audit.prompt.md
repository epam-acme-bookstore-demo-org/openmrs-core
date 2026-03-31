---
description: Audit a codebase area for complexity issues, oversized files, and performance anti-patterns
argument-hint: "Folder path or module to audit (e.g., 'src/core/')"
---

# Complexity Audit

Perform a structural and algorithmic audit of a codebase area.

## Target

${input:target}

## Instructions

1. Load the **performance-code-quality** skill.
2. List all files in the target area with their line counts.
3. Flag every file exceeding ~400 lines — report the line count and main responsibilities.
4. For each file, identify functions exceeding ~30 statements — report name, line count, and nesting depth.
5. Scan for algorithmic issues:
   - Nested loops over the same collection (O(n²))
   - Repeated linear scans where a set/map would give O(1)
   - Unbatched I/O in loops (N queries, N HTTP calls, N file reads)
   - Missing caching for repeated expensive calls
6. Produce a summary table:

   | File | Lines | God functions | O(n²) risks | Unbatched I/O | Score |
   |------|-------|---------------|-------------|---------------|-------|

   Score: 🟢 clean, 🟡 needs attention, 🔴 critical.

7. Recommend the top-5 highest-impact improvements with concrete refactoring steps.
