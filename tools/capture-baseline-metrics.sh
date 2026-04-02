#!/usr/bin/env bash
# ==============================================================================
# capture-baseline-metrics.sh
# ==============================================================================
# Non-destructive static analysis script for OpenMRS Core modernisation.
# Counts code quality indicators using grep/find/wc — no compilation needed.
#
# Usage:
#   bash tools/capture-baseline-metrics.sh [OPTIONS]
#
# Options:
#   --help, -h          Show this help message and exit
#   --format FORMAT     Output format: markdown (default) | json
#   --modules MODULES   Comma-separated module list (default: api,web)
#   --include-tests     Also scan test source trees (default: production only)
#   --snapshot FILE     Write output to a snapshot file for later comparison
#   --diff FILE         Compare current run against a previous snapshot file
#
# Run from the repository root:
#   bash tools/capture-baseline-metrics.sh
#   bash tools/capture-baseline-metrics.sh --format json
#   bash tools/capture-baseline-metrics.sh --snapshot metrics-$(date +%Y%m%d).md
#   bash tools/capture-baseline-metrics.sh --diff metrics-20250101.md
#
# Exit codes:
#   0  — completed successfully
#   1  — fatal error (wrong directory, missing tools)
#   2  — diff shows regressions vs snapshot
# ==============================================================================

set -euo pipefail

# ── Constants ──────────────────────────────────────────────────────────────────
SCRIPT_NAME="$(basename "$0")"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_VERSION="1.0.0"

# ── Defaults ───────────────────────────────────────────────────────────────────
FORMAT="markdown"
MODULES="api,web"
INCLUDE_TESTS=false
SNAPSHOT_FILE=""
DIFF_FILE=""

# ── Colour helpers (disabled when not a TTY) ───────────────────────────────────
if [ -t 1 ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
  CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; BOLD=''; RESET=''
fi

# ── Help ───────────────────────────────────────────────────────────────────────
usage() {
  cat <<EOF
${BOLD}${SCRIPT_NAME} v${SCRIPT_VERSION}${RESET}

Non-destructive baseline metrics capture for OpenMRS Core modernisation.
Runs static analysis (grep/find/wc) — no build or compilation required.

${BOLD}USAGE${RESET}
  bash ${SCRIPT_NAME} [OPTIONS]

${BOLD}OPTIONS${RESET}
  -h, --help              Show this help message and exit
  --format FORMAT         Output format: ${BOLD}markdown${RESET} (default) | ${BOLD}json${RESET}
  --modules MODULES       Comma-separated modules to scan (default: api,web)
                          Available: api, web, webapp, liquibase, test, test-suite
  --include-tests         Also scan src/test/java trees (default: production only)
  --snapshot FILE         Write output to FILE for future diff comparisons
  --diff FILE             Compare current results against a previous snapshot

${BOLD}EXAMPLES${RESET}
  # Standard run — Markdown output to stdout
  bash tools/capture-baseline-metrics.sh

  # JSON output (useful for CI artefacts)
  bash tools/capture-baseline-metrics.sh --format json

  # Save a named snapshot
  bash tools/capture-baseline-metrics.sh --snapshot docs/snapshots/baseline-\$(date +%Y%m%d).md

  # Compare against a previous snapshot to detect regressions
  bash tools/capture-baseline-metrics.sh --diff docs/snapshots/baseline-20250101.md

  # Scan test code too
  bash tools/capture-baseline-metrics.sh --include-tests --modules api

${BOLD}METRICS CAPTURED${RESET}
  Category            │ Metric
  ────────────────────┼──────────────────────────────────────────────
  File inventory      │ Production files, lines, test files
  Java 21 adoption    │ var, records, sealed, text blocks, switch →, instanceof
  Code smells         │ God files, broad catches, silent catches, boolean params
  Tech debt markers   │ @Deprecated, @SuppressWarnings, TODO/FIXME, legacy APIs
  Build health        │ @Test count, legacy java.util.Date imports

${BOLD}NOTES${RESET}
  • All counts are approximations based on textual pattern matching.
    SpotBugs and Checkstyle (via Maven) provide authoritative numbers.
  • The 'var' counter can over-count non-declaration uses; treat as indicative.
  • Boolean-param counts match method signatures only; field declarations excluded.
  • Run from the repository root directory.

EOF
}

# ── Argument parsing ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --format)
      FORMAT="${2:?'--format requires an argument: markdown or json'}"
      shift 2
      ;;
    --modules)
      MODULES="${2:?'--modules requires a comma-separated module list'}"
      shift 2
      ;;
    --include-tests)
      INCLUDE_TESTS=true
      shift
      ;;
    --snapshot)
      SNAPSHOT_FILE="${2:?'--snapshot requires a file path'}"
      shift 2
      ;;
    --diff)
      DIFF_FILE="${2:?'--diff requires a file path'}"
      shift 2
      ;;
    *)
      echo "${RED}Unknown option: $1${RESET}" >&2
      echo "Run '${SCRIPT_NAME} --help' for usage." >&2
      exit 1
      ;;
  esac
done

# ── Pre-flight checks ──────────────────────────────────────────────────────────
cd "$REPO_ROOT"

if [[ ! -f "pom.xml" ]]; then
  echo "${RED}ERROR: pom.xml not found. Run this script from the repository root.${RESET}" >&2
  exit 1
fi

if [[ -n "$DIFF_FILE" && ! -f "$DIFF_FILE" ]]; then
  echo "${RED}ERROR: diff target not found: ${DIFF_FILE}${RESET}" >&2
  exit 1
fi

for cmd in grep find wc awk sort; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "${RED}ERROR: required tool '${cmd}' not found in PATH.${RESET}" >&2
    exit 1
  fi
done

# ── Source path builder ────────────────────────────────────────────────────────
build_source_paths() {
  local paths=()
  IFS=',' read -ra mods <<< "$MODULES"
  for mod in "${mods[@]}"; do
    mod="$(echo "$mod" | tr -d ' ')"
    local prod="${mod}/src/main/java"
    [[ -d "$prod" ]] && paths+=("$prod")
    if [[ "$INCLUDE_TESTS" == true ]]; then
      local tst="${mod}/src/test/java"
      [[ -d "$tst" ]] && paths+=("$tst")
    fi
  done
  echo "${paths[@]}"
}

# ── Count helpers ──────────────────────────────────────────────────────────────

# Count files matching a find pattern; args: <path...> (all dirs to search)
count_java_files() {
  local count=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    n=$(find "$p" -name "*.java" | wc -l | tr -d ' ')
    count=$((count + n))
  done
  echo "$count"
}

# Count total lines in Java files across given paths
count_java_lines() {
  local total=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    n=$(find "$p" -name "*.java" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}')
    total=$((total + ${n:-0}))
  done
  echo "$total"
}

# Count files where line count exceeds threshold
count_god_files() {
  local threshold="${1:-400}"
  shift
  local count=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    n=$(find "$p" -name "*.java" -exec wc -l {} + 2>/dev/null \
        | awk -v t="$threshold" 'NF==2 && $2!="total" && $1 > t {c++} END{print c+0}')
    count=$((count + n))
  done
  echo "$count"
}

# Count non-comment grep matches (excludes lines starting with // or *)
count_grep_pattern() {
  local pattern="$1"
  shift
  local count=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    n=$(set +o pipefail; grep -r --include="*.java" -l "$pattern" "$p" 2>/dev/null \
        | xargs -I{} grep --include="*.java" "$pattern" {} 2>/dev/null \
        | grep -v '^\s*//' | grep -v '^\s*\*' | wc -l | tr -d ' ')
    count=$((count + ${n:-0}))
  done
  echo "$count"
}

# Simpler single-pass count (used where pattern is unambiguous)
count_grep_simple() {
  local pattern="$1"
  shift
  local total=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    # grep exits 1 when no matches; set +o pipefail so that doesn't kill us.
    # wc -l always outputs a number, so n is always a clean integer.
    n=$(set +o pipefail; grep -r --include="*.java" "$pattern" "$p" 2>/dev/null \
        | grep -v '^\s*//' | grep -v '^\s*\*' | wc -l | tr -d ' ')
    total=$((total + ${n:-0}))
  done
  echo "$total"
}

# Count silent catch blocks: catch(...) { } with no real statement inside
# A "real statement" is any non-blank, non-comment line.
count_silent_catches() {
  local total=0
  for p in "$@"; do
    [[ -d "$p" ]] || continue
    local n
    n=$(python3 - "$p" 2>/dev/null <<'PYEOF'
import re, os, sys

def count_silent_catches(root):
    count = 0
    for dirpath, dirs, files in os.walk(root):
        for fname in files:
            if not fname.endswith('.java'):
                continue
            fpath = os.path.join(dirpath, fname)
            try:
                with open(fpath, encoding='utf-8', errors='replace') as fh:
                    lines = fh.readlines()
            except OSError:
                continue
            i = 0
            while i < len(lines):
                if re.search(r'\bcatch\s*\(', lines[i]):
                    # Scan forward to find the catch block body
                    depth = 0
                    body = []
                    in_block = False
                    j = i
                    while j < len(lines) and j < i + 40:
                        for ch in lines[j]:
                            if ch == '{':
                                depth += 1
                                in_block = True
                            elif ch == '}':
                                depth -= 1
                        if in_block:
                            body.append(lines[j])
                        if in_block and depth == 0:
                            break
                        j += 1
                    # Body lines between opening { and closing }
                    real_code = False
                    for bl in body[1:-1]:
                        s = bl.strip()
                        if s and not s.startswith('//') \
                             and not s.startswith('*') \
                             and not s.startswith('/*'):
                            real_code = True
                            break
                    if not real_code and len(body) > 1:
                        count += 1
                    i = j
                i += 1
    return count

if len(sys.argv) > 1:
    print(count_silent_catches(sys.argv[1]))
else:
    print(0)
PYEOF
)
    total=$((total + ${n:-0}))
  done
  echo "$total"
}

# List top N god files with line counts
list_top_god_files() {
  local n="${1:-10}"
  shift
  find "$@" -name "*.java" 2>/dev/null \
    | xargs wc -l 2>/dev/null \
    | awk 'NF==2 && $2!="total" && $1 > 400 {print $1, $2}' \
    | sort -rn \
    | head -"$n"
}

# ── Collect all metrics ────────────────────────────────────────────────────────
collect_metrics() {
  read -ra SRC_PATHS <<< "$(build_source_paths)"

  # Separator for progress messages (stderr so they don't pollute captured output)
  progress() { echo "${CYAN}  ▸ $*${RESET}" >&2; }

  echo "${BOLD}Scanning source paths: ${SRC_PATHS[*]}${RESET}" >&2
  echo "" >&2

  # ── 1. File inventory ────────────────────────────────────────────────────────
  progress "File inventory..."
  M_PROD_FILES=$(count_java_files "${SRC_PATHS[@]}")
  M_PROD_LINES=$(count_java_lines "${SRC_PATHS[@]}")

  # Test files (always collect separately for inventory, regardless of --include-tests)
  local test_paths=()
  IFS=',' read -ra mods <<< "$MODULES"
  for mod in "${mods[@]}"; do
    mod="$(echo "$mod" | tr -d ' ')"
    local tst="${mod}/src/test/java"
    [[ -d "$tst" ]] && test_paths+=("$tst")
  done
  M_TEST_FILES=$(count_java_files "${test_paths[@]}")
  M_TEST_COUNT=$(set +o pipefail; grep -r --include="*.java" '@Test' "${test_paths[@]}" 2>/dev/null | wc -l | tr -d ' ')

  # ── 2. Java 21 feature adoption ──────────────────────────────────────────────
  progress "Java 21 feature adoption..."
  # var: match 'var ' as local variable declaration; excludes comments
  M_VAR=$(count_grep_simple '\bvar [a-zA-Z_$]' "${SRC_PATHS[@]}")
  # records: 'record ClassName(' at class declaration level
  M_RECORDS=$(count_grep_simple '^\s*\(public\s\+\|private\s\+\|protected\s\+\|\)record\s\+[A-Z]' "${SRC_PATHS[@]}")
  # sealed classes
  M_SEALED=$(count_grep_simple '\bsealed\b.*\bclass\b\|\bsealed\b.*\binterface\b' "${SRC_PATHS[@]}")
  # text blocks
  M_TEXT_BLOCKS=$(count_grep_simple '"""' "${SRC_PATHS[@]}")
  # switch expressions: arrow-style case labels (→ Java 14+)
  M_SWITCH_EXPR=$(count_grep_simple 'case .*->' "${SRC_PATHS[@]}")
  # pattern-matching instanceof: 'instanceof TypeName varName'
  M_PATTERN_INSTANCEOF=$(count_grep_simple 'instanceof [A-Z][a-zA-Z0-9_]* [a-z]' "${SRC_PATHS[@]}")
  # Total Java 21 feature uses
  M_JAVA21_TOTAL=$((M_VAR + M_RECORDS + M_SEALED + M_TEXT_BLOCKS + M_SWITCH_EXPR + M_PATTERN_INSTANCEOF))

  # ── 3. Code smells ───────────────────────────────────────────────────────────
  progress "Code smells..."
  M_GOD_FILES=$(count_god_files 400 "${SRC_PATHS[@]}")
  M_GOD_FILES_800=$(count_god_files 800 "${SRC_PATHS[@]}")
  M_BROAD_CATCH=$(count_grep_simple 'catch\s*(Exception\b' "${SRC_PATHS[@]}")
  M_CATCH_THROWABLE=$(count_grep_simple 'catch\s*(Throwable\b' "${SRC_PATHS[@]}")
  # Boolean params in method signatures (public/protected/private/default methods).
  # Uses a self-contained -E pattern per path to avoid passing paths as the grep pattern.
  M_BOOL_PARAMS=0
  for _p in "${SRC_PATHS[@]}"; do
    [[ -d "$_p" ]] || continue
    _n=$(grep -rh --include="*.java" \
           -E '^\s*(public|protected|private|default)\s[^{;]+\bboolean\b' \
           "$_p" 2>/dev/null \
         | grep -v '^\s*//' | wc -l | tr -d ' ' || echo 0)
    M_BOOL_PARAMS=$((M_BOOL_PARAMS + ${_n:-0}))
  done
  unset _p _n
  M_SILENT_CATCHES=$(count_silent_catches "${SRC_PATHS[@]}")

  # ── 4. Tech debt markers ────────────────────────────────────────────────────
  progress "Tech debt markers..."
  M_DEPRECATED=$(count_grep_simple '@Deprecated' "${SRC_PATHS[@]}")
  M_SUPPRESS=$(count_grep_simple '@SuppressWarnings' "${SRC_PATHS[@]}")
  M_TODO=$(set +o pipefail; grep -r --include="*.java" '\bTODO\b\|\bFIXME\b' "${SRC_PATHS[@]}" 2>/dev/null | wc -l | tr -d ' ')
  M_DATE_IMPORT=$(count_grep_simple 'import java\.util\.Date' "${SRC_PATHS[@]}")
  M_STRING_SWITCH=$(count_grep_simple 'case "[^"]*":' "${SRC_PATHS[@]}")
  M_SECURITY_MANAGER=$(count_grep_simple 'SecurityManager\b' "${SRC_PATHS[@]}")

  # ── 5. Git metadata ─────────────────────────────────────────────────────────
  M_GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
  M_GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
  M_TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
}

# ── Markdown output ────────────────────────────────────────────────────────────
render_markdown() {
  cat <<MDEOF
# OpenMRS Core — Baseline Metrics Snapshot

> **Generated**: ${M_TIMESTAMP}
> **Commit**: \`${M_GIT_COMMIT}\` on \`${M_GIT_BRANCH}\`
> **Modules scanned**: ${MODULES}
> **Includes test sources**: ${INCLUDE_TESTS}
> **Script version**: ${SCRIPT_VERSION}

---

## 1. File Inventory

| Metric | Count |
|---|---:|
| Production Java files | ${M_PROD_FILES} |
| Production lines of code | ${M_PROD_LINES} |
| Test Java files | ${M_TEST_FILES} |
| \`@Test\` annotations (test count) | ${M_TEST_COUNT} |

---

## 2. Java 21 Feature Adoption

> **Goal**: Increase these counts as modernisation progresses.
> Phase 1 targets are defined in \`docs/baseline-metrics.md\`.

| Feature | Current Count | Phase 1 Target | Status |
|---|---:|---:|---|
| \`var\` local type inference | ${M_VAR} | ≥ 100 | $([ "${M_VAR}" -ge 100 ] && echo "✅" || echo "⏳") |
| Records | ${M_RECORDS} | ≥ 5 | $([ "${M_RECORDS}" -ge 5 ] && echo "✅" || echo "⏳") |
| Sealed classes | ${M_SEALED} | ≥ 1 | $([ "${M_SEALED}" -ge 1 ] && echo "✅" || echo "⏳") |
| Text blocks | ${M_TEXT_BLOCKS} | ≥ 10 | $([ "${M_TEXT_BLOCKS}" -ge 10 ] && echo "✅" || echo "⏳") |
| Switch expressions (\`->\`) | ${M_SWITCH_EXPR} | ≥ 20 | $([ "${M_SWITCH_EXPR}" -ge 20 ] && echo "✅" || echo "⏳") |
| Pattern-matching \`instanceof\` | ${M_PATTERN_INSTANCEOF} | ≥ 50 | $([ "${M_PATTERN_INSTANCEOF}" -ge 50 ] && echo "✅" || echo "⏳") |
| **Total Java 21 uses** | **${M_JAVA21_TOTAL}** | **≥ 186** | |

---

## 3. Code Smells

> **Goal**: Decrease these counts as modernisation progresses.
> A metric must not increase beyond its baseline value (regression gate).

| Smell | Current Count | Baseline | Regression? |
|---|---:|---:|---|
| God files (> 400 lines) | ${M_GOD_FILES} | 92 | $([ "${M_GOD_FILES}" -gt 92 ] && echo "🔴 YES" || echo "✅ No") |
| God files (> 800 lines) | ${M_GOD_FILES_800} | 39 | $([ "${M_GOD_FILES_800}" -gt 39 ] && echo "🔴 YES" || echo "✅ No") |
| Broad catch \`(Exception)\` | ${M_BROAD_CATCH} | 256 | $([ "${M_BROAD_CATCH}" -gt 256 ] && echo "🔴 YES" || echo "✅ No") |
| Catch \`(Throwable)\` | ${M_CATCH_THROWABLE} | 1 | $([ "${M_CATCH_THROWABLE}" -gt 1 ] && echo "🔴 YES" || echo "✅ No") |
| Silent catch blocks | ${M_SILENT_CATCHES} | 4 | $([ "${M_SILENT_CATCHES}" -gt 4 ] && echo "🔴 YES" || echo "✅ No") |
| Boolean parameters in signatures | ${M_BOOL_PARAMS} | 499 | $([ "${M_BOOL_PARAMS}" -gt 499 ] && echo "🔴 YES" || echo "✅ No") |
| String \`switch\` dispatch | ${M_STRING_SWITCH} | 19 | $([ "${M_STRING_SWITCH}" -gt 19 ] && echo "🔴 YES" || echo "✅ No") |

---

## 4. Tech Debt Markers

| Marker | Current Count | Baseline | Regression? |
|---|---:|---:|---|
| \`@Deprecated\` annotations | ${M_DEPRECATED} | 123 | $([ "${M_DEPRECATED}" -gt 123 ] && echo "🔴 YES" || echo "✅ No") |
| \`@SuppressWarnings\` annotations | ${M_SUPPRESS} | 116 | $([ "${M_SUPPRESS}" -gt 116 ] && echo "🔴 YES" || echo "✅ No") |
| TODO / FIXME comments | ${M_TODO} | — | — |
| \`java.util.Date\` imports | ${M_DATE_IMPORT} | 170 | $([ "${M_DATE_IMPORT}" -gt 170 ] && echo "🔴 YES" || echo "✅ No") |
| \`SecurityManager\` references | ${M_SECURITY_MANAGER} | — | — |

---

## 5. Top God Files (Current Run)

> Files exceeding 400 lines. Listed by size descending, capped at 10.

| Lines | File |
|---:|---|
MDEOF

  list_top_god_files 10 "${SRC_PATHS[@]}" | while read -r lines file; do
    # Trim repo-root prefix for readability
    short_file="${file#${REPO_ROOT}/}"
    echo "| ${lines} | \`${short_file}\` |"
  done

  cat <<MDEOF

---

*Generated by \`tools/capture-baseline-metrics.sh\` — re-run at any time to get a fresh snapshot.*
*Methodology and interpretation: see \`docs/baseline-metrics.md\`.*
MDEOF
}

# ── JSON output ────────────────────────────────────────────────────────────────
render_json() {
  cat <<JSONEOF
{
  "meta": {
    "generated_at": "${M_TIMESTAMP}",
    "git_commit": "${M_GIT_COMMIT}",
    "git_branch": "${M_GIT_BRANCH}",
    "modules_scanned": "${MODULES}",
    "includes_test_sources": ${INCLUDE_TESTS},
    "script_version": "${SCRIPT_VERSION}"
  },
  "file_inventory": {
    "production_java_files": ${M_PROD_FILES},
    "production_lines_of_code": ${M_PROD_LINES},
    "test_java_files": ${M_TEST_FILES},
    "test_annotation_count": ${M_TEST_COUNT}
  },
  "java21_adoption": {
    "var_uses": ${M_VAR},
    "records": ${M_RECORDS},
    "sealed_classes": ${M_SEALED},
    "text_blocks": ${M_TEXT_BLOCKS},
    "switch_expressions": ${M_SWITCH_EXPR},
    "pattern_matching_instanceof": ${M_PATTERN_INSTANCEOF},
    "total": ${M_JAVA21_TOTAL}
  },
  "code_smells": {
    "god_files_400": ${M_GOD_FILES},
    "god_files_800": ${M_GOD_FILES_800},
    "broad_catch_exception": ${M_BROAD_CATCH},
    "catch_throwable": ${M_CATCH_THROWABLE},
    "silent_catches": ${M_SILENT_CATCHES},
    "boolean_params_in_signatures": ${M_BOOL_PARAMS},
    "string_switch_cases": ${M_STRING_SWITCH}
  },
  "tech_debt": {
    "deprecated_annotations": ${M_DEPRECATED},
    "suppress_warnings_annotations": ${M_SUPPRESS},
    "todo_fixme_comments": ${M_TODO},
    "java_util_date_imports": ${M_DATE_IMPORT},
    "security_manager_references": ${M_SECURITY_MANAGER}
  }
}
JSONEOF
}

# ── Diff against snapshot ──────────────────────────────────────────────────────
run_diff() {
  local snap="$1"
  echo "${BOLD}Comparing against snapshot: ${snap}${RESET}" >&2

  # Extract numeric values from snapshot using grep patterns
  extract_from_snapshot() {
    local label="$1"
    # Look for Markdown table row pattern: | label | N | (plain or bold **N**)
    # set +o pipefail because grep returns 1 when no match found
    set +o pipefail
    grep -i "$label" "$snap" 2>/dev/null \
      | sed 's/\*\*//g' \
      | grep -oE '\| [0-9,]+ \|' | head -1 \
      | grep -oE '[0-9,]+' | tr -d ',' | head -1
    set -o pipefail
  }

  echo "" >&2
  echo "${BOLD}── Regression check (current vs snapshot) ──────────────────${RESET}" >&2
  echo "" >&2

  local regressions=0

  check_metric() {
    local name="$1" current="$2" snap_label="$3" direction="$4"
    local snap_val
    snap_val=$(extract_from_snapshot "$snap_label")
    if [[ -z "$snap_val" ]]; then
      printf "  %-40s current=%-8s snapshot=%-8s  %s\n" "$name" "$current" "N/A" "${YELLOW}(not in snapshot)${RESET}" >&2
      return
    fi
    if [[ "$direction" == "up" ]]; then
      # Higher is better (Java 21 adoption)
      if [[ "$current" -lt "$snap_val" ]]; then
        printf "  %-40s current=%-8s snapshot=%-8s  %s\n" "$name" "$current" "$snap_val" "${RED}🔴 REGRESSION${RESET}" >&2
        regressions=$((regressions + 1))
      else
        printf "  %-40s current=%-8s snapshot=%-8s  %s\n" "$name" "$current" "$snap_val" "${GREEN}✅ OK${RESET}" >&2
      fi
    else
      # Lower is better (smells/debt)
      if [[ "$current" -gt "$snap_val" ]]; then
        printf "  %-40s current=%-8s snapshot=%-8s  %s\n" "$name" "$current" "$snap_val" "${RED}🔴 REGRESSION${RESET}" >&2
        regressions=$((regressions + 1))
      else
        printf "  %-40s current=%-8s snapshot=%-8s  %s\n" "$name" "$current" "$snap_val" "${GREEN}✅ OK${RESET}" >&2
      fi
    fi
  }

  check_metric "Java 21 total uses"           "$M_JAVA21_TOTAL"     "Total Java 21"           "up"
  check_metric "var uses"                     "$M_VAR"              "var.*local"               "up"
  check_metric "Records"                      "$M_RECORDS"          "Records"                  "up"
  check_metric "God files (>400)"             "$M_GOD_FILES"        "God files.*400"           "down"
  check_metric "Broad catch (Exception)"      "$M_BROAD_CATCH"      "Broad catch"              "down"
  check_metric "Silent catches"               "$M_SILENT_CATCHES"   "Silent catch"             "down"
  check_metric "Boolean params"               "$M_BOOL_PARAMS"      "Boolean param"            "down"
  check_metric "@Deprecated annotations"      "$M_DEPRECATED"       "Deprecated"               "down"
  check_metric "@SuppressWarnings"            "$M_SUPPRESS"         "SuppressWarnings"         "down"
  check_metric "java.util.Date imports"       "$M_DATE_IMPORT"      "util.Date"                "down"

  echo "" >&2
  if [[ $regressions -gt 0 ]]; then
    echo "${RED}${BOLD}⚠ ${regressions} regression(s) detected vs snapshot.${RESET}" >&2
    return 2
  else
    echo "${GREEN}${BOLD}✅ No regressions detected vs snapshot.${RESET}" >&2
    return 0
  fi
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  echo "" >&2
  echo "${BOLD}${CYAN}OpenMRS Core — Baseline Metrics Capture${RESET}" >&2
  echo "${CYAN}════════════════════════════════════════${RESET}" >&2
  echo "" >&2

  collect_metrics

  echo "" >&2
  echo "${GREEN}Collection complete. Rendering output...${RESET}" >&2
  echo "" >&2

  # Build output
  local output=""
  case "$FORMAT" in
    markdown|md) output=$(render_markdown) ;;
    json)        output=$(render_json) ;;
    *)
      echo "${RED}Unknown format '${FORMAT}'. Use: markdown | json${RESET}" >&2
      exit 1
      ;;
  esac

  # Write to snapshot file if requested
  if [[ -n "$SNAPSHOT_FILE" ]]; then
    mkdir -p "$(dirname "$SNAPSHOT_FILE")"
    echo "$output" > "$SNAPSHOT_FILE"
    echo "${GREEN}Snapshot written to: ${SNAPSHOT_FILE}${RESET}" >&2
  fi

  # Always print to stdout
  echo "$output"

  # Run diff if requested
  if [[ -n "$DIFF_FILE" ]]; then
    echo "" >&2
    run_diff "$DIFF_FILE"
    local diff_exit=$?
    [[ $diff_exit -ne 0 ]] && exit 2
  fi
}

main "$@"
