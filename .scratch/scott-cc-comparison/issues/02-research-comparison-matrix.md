Type: research
Status: resolved
Blocked by: (none)

## Question

What are the exact feature differences between Scott-CC's mutation-testing plugin and OMP's implementation across all dimensions (engine, isolation, strategies, quality analysis, refactoring, interface, safety)?

### Background

Scott-CC's plugin lives at `citadelgrad/scott-cc/tree/main/plugins/mutation-testing/` — a 5-agent Claude Code plugin system for Python/JS projects using LLM-guided semantic mutations and git worktrees.

OMP's implementation lives in `.omp/` of this repo — a 5-agent OMP system for Kotlin projects using mutflow (compile-once meta-mutant) with a typed Kotlin result-parsing module in buildSrc.

The prior wayfinder maps (`.scratch/mutation-testing-omp/`, `.scratch/mutation-results-module/`) document the porting decisions already made. This ticket goes beyond those maps to identify **residual gaps** — Scott-CC features OMP does not have, and OMP features Scott-CC does not have.

### Task

1. Read all Scott-CC plugin files: agents (test-saboteur, test-executor, test-auditor, test-refactor-specialist, test-quality-reviewer), skills/mutation-test/SKILL.md, commands/mutation-test.md, docs/MUTATION-TESTING.md, tests/verify-worktree-isolation.sh, .claude-plugin/plugin.json, tests/fixtures/contract-handoff.json.
2. Read all OMP agent files in `.omp/agents/`, the skill at `.omp/skills/mutation-test/SKILL.md`, and the Gradle task at `.omp/mutation-results.gradle.kts`.
3. Produce a detailed comparison matrix (one row per feature, columns: Scott-CC, OMP, gap direction, effort to close).
4. Focus on: mutation strategies, quality analysis features (zombie detection, redundant groups, over-mocked, missing coverage), test refactoring (auto-generated vs suggestions), interface (triggers, modes, setup), safety features, and parallelization model.

### Acceptance criteria

- Side-by-side feature matrix with gap direction and estimated effort.
- List of Scott-CC features OMP lacks.
- List of OMP features Scott-CC lacks.
- Findings captured in this issue's resolution.


## Answer

**R2 (ResearchComparisonMatrix) complete.** Full comparison matrix written to `.scratch/scott-cc-comparison/research/02-comparison-matrix.md`.

### Key findings

**Scott-CC features OMP lacks (10 residual gaps):**
1. **Redundant test group detection** — tests that always fail together (>5 in same failure signature) flagged for consolidation. No fork branch addresses this. Effort: L.
2. **Auto-generated refactored test code** — Scott-CC produces production-ready full test files; OMP produces suggestions only. Effort: L.
3. **`--auto-approve` for auto-apply** — Scott-CC can apply refactoring without second confirmation. Effort: M.
4. **Natural language triggers** — auto-detection on "mutation test", "zombie tests", etc. Effort: XL.
5. **Quick/standard/deep mode abstraction** — `--quick` (5), `--standard` (15), `--deep` (30+) mutation count modes. Effort: M.
6. **`--focus=<area>` parameter** — limit mutations to specific code area. Partially bridgeable via fork `add-option-to-define-mutation-targets-via-gradle-config` (class-level scoping). Effort: S.
7. **Diff generation before applying refactoring** — Full git diff in report. Effort: S.
8. **Explicit rollback instructions** — Provided in report. Effort: trivial.
9. **Confidence intervals (statistical CI)** — e.g., 3/15 → 5–45% 95% CI. Effort: S.
10. **Execution gap reporting** — ERROR/INVALID_MUTATION excluded from score denominator. Effort: S.

**OMP features Scott-CC lacks (5):**
1. **Confidence levels** (Low/Medium/High by mutation count) with explicit thresholds.
2. **Typed Kotlin JSON result module** — `@Serializable` data classes in buildSrc, 18 unit tests, backward-compatible JSON schema.
3. **Verification modes** (STRICT/LENIENT/DISABLED) — via fork `introduce-verification-mode-strict-lenient-and-disabled`.
4. **CLI safe-guard script** — `mutflow-verify-jar.sh` prevents mutated artifacts reaching production — via fork `optional-extra-cli-safe-guard-verification`.
5. **Setup subcommand** — `/mutation-test setup` bootstraps `.omp/` + buildSrc into new projects.

**Both-different (mutflow fork branches bridge 4 Scott-CC gaps):**
- `feature/exception-type-swap` → closes Strategy 5 (exception types)
- `feature/zombie-detection` → closes multi-killer tracking (full per-test-per-mutation matrix)
- `double-arithmetic-ir-when-truncate-fix` → correctness fix for arithmetic operator
- `avoid-mutating-null-checks` → mutation quality improvement (noise reduction)

**Architectural tradeoff (not closable without engine rewrite):**
- Scott-CC's git-worktree-per-mutation enables 15× parallel test execution; OMP's compile-once meta-mutant serializes mutations via mutflow's global synchronized lock. This is inherent to mutflow's architecture.

Findings capture: research/02-comparison-matrix.md