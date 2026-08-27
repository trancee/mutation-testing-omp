# R2 Comparison Matrix: Scott-CC vs OMP Mutation-Testing

**Status:** Research findings — resolved  
**Date:** 2026-08-26  
**Author:** ResearchComparisonMatrix (R2)  
**Source files:** Scott-CC plugin (`citadelgrad/scott-cc/plugins/mutation-testing/`), OMP implementation (`.omp/` local), mutflow fork branches (`trancee/mutflow-exception-swap`, 12 branches inspected)  
**See also:** `.scratch/scott-cc-comparison/domain-model.md`, `issues/01-research-mutflow-fork-branches.md` (R1), `issues/02-research-comparison-matrix.md`

---

## Legend

| Term | Meaning |
|---|---|
| Scott-CC | Claude Code plugin for Python/JS — LLM-guided semantic mutations, git worktrees |
| OMP | Agent harness for Kotlin (JVM) — mutflow compiler plugin, compile-once meta-mutant |
| Fork | `trancee/mutflow-exception-swap` branch of mutflow (12 branches, all diverging from a pre-master baseline) |
| Gap direction | Scott-CC-only: Scott-CC has a feature OMP lacks; OMP-only: OMP has a feature Scott-CC lacks; Both-different: both have the feature but implemented differently; Both-have: equivalent |
| Effort | S = small (<1 day), M = medium (2-5 days), L = large (1-2 weeks), XL = extra-large (3+ weeks) |

---

## 1. Mutation Strategies

| # | Feature | Scott-CC | OMP | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 1.1 | **Strategy 1: Boundary conditions** | `>=` → `>`, `==`, `<=`; LLM chooses per-file; also `==` → `!=` | `RelationalComparisonOperator` (`>` ↔ `>=`, `<` ↔ `<=`), `ConstantBoundaryOperator` | Both-different | N/A |
| 1.2 | **Strategy 2: Return values** | `return x` → `return None/""/wrong value`; LLM picks context-appropriate substitutes | `BooleanReturnOperator`, `NullableReturnOperator` | Both-different | N/A |
| 1.3 | **Strategy 3: Boolean logic** | `and` → `or`, `True` → `False`, negation, remove condition | `BooleanInversionOperator`, `EqualitySwapOperator`, `BooleanLogicOperator` | Both-different | N/A |
| 1.4 | **Strategy 4: Arithmetic operators** | `*` → `/`, `+`, `-`; LLM avoids magic numbers | `ArithmeticOperator` | Both-different | N/A |
| 1.5 | **Strategy 5: Exception types** | `raise ValueError()` → `TypeError()`; one of the 5 core strategies | **No mutflow operator upstream.** Covered by **fork** `feature/exception-type-swap` via `ExceptionTypeSwapOperator` | Gap, partially bridged by fork | S (cherry-pick fork branch) |
| 1.6 | **Operator selection mechanism** | LLM semantically decides which mutations to apply per file (intelligent targeting) | Predefined static catalog only — all operators applied to all targets | Scott-CC-only | L (LLM targeting layer over mutflow IR) |
| 1.7 | **Exception type swap operator API** | N/A (Python `raise` statements) | `ExceptionTypeSwapOperator` extends `ConstructorMutationOperator`; uses `visitThrow` to swap thrown exception class FQNs via `MutationRegistry.check()` | OMP-only (via fork) | Already implemented in fork |
| 1.8 | **Arithmetic IR truncate fix** | N/A (Python has no IR) | **Bug fix** in fork `double-arithmetic-ir-when-truncate-fix`: `IrWhenImpl` was hardcoded to `booleanType` instead of `original.type`, causing `Double` arithmetic to lose fractional precision (e.g., `50.0 * 0.05` → `2.0` instead of `2.5`). | Both-have (bug fix in fork only) | S (cherry-pick fork branch) |
| 1.9 | **Null check mutation suppression** | N/A (Python `None` vs JS `null` handled contextually by LLM) | Fork `avoid-mutating-null-checks`: `EqualitySwapOperator` skips `==` and `!=` when either operand is `null` literal, preventing confusing mutations on Kotlin `?.`/`?:` desugared null checks | OMP-only (via fork) | S (cherry-pick fork branch) |
| 1.10 | **Mutation count modes** | Quick (5), Standard (15), Deep (30+) — user controls via `--quick`/`--deep` flags | Mutflow controls mutation count via `@MutFlowTest(maxRuns=N)`; no quick/standard/deep modes | Scott-CC-only | M (add user-facing mode abstraction over mutflow's maxRuns) |

---

## 2. Quality Analysis

| # | Feature | Scott-CC (test-auditor) | OMP (test-auditor + Gradle task) | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 2.1 | **Mutation score** | `killed / executable_mutations * 100` (excludes ERROR/INVALID) | `killed / total` (all mutation results) | Both-have | N/A |
| 2.2 | **Quality bands** | Excellent >80% & zombie% <10%, Good >60% & zombie% <20%, Fair >40%, Poor else | Excellent >80%, Good >60%, Fair >30%, Poor ≤30% | Both-different | Trivial to align bands |
| 2.3 | **Confidence levels** | Sample-size recommendations (small/medium/large) + statistical CI formula | Explicit: Low (<10 mutations), Medium (10–50), High (50+) | OMP-only | S to add OMP-style confidence to Scott-CC |
| 2.4 | **Zombie test detection** | Tests that never failed across all mutations; uses `test_outcomes` map per test per mutation | Full per-test-per-mutation matrix via `testKillerMatrix` from typed JSON module; zombie candidates = tests with no entry in matrix | Both-have (OMP more precise) | N/A |
| 2.5 | **All-killer tracking (multi-killer)** | Each executor reports one `test_outcomes` per mutation; auditor intersects per-test across mutations to find zombies | Fork `feature/zombie-detection`: `MutationResult.Killed` changed from `testName: String` to `testNames: Set<String>` capturing ALL tests that kill each mutation; `printSummary()` emits multiple `killed by:` lines | OMP-only (via fork) | S (cherry-pick fork branch) |
| 2.6 | **Redundant test groups** | Tests that always fail together (>5 in same failure signature) → consolidate recommendation | **Not present.** No redundancy detection in OMP auditor or Gradle task | Scott-CC-only | L (implement failure-signature grouping algorithm in test-auditor) |
| 2.7 | **Over-mocked test detection** | Count `unittest.mock`/`@patch` decorators; flag >5 mocks per test | Count MockK `mockk()`/`spyk()`/`@MockK` and Mockito `mock()`/`@Mock`; flag >3 mocks per test | Both-have (different thresholds) | Trivial to align thresholds |
| 2.8 | **Missing coverage / surviving mutation analysis** | Surviving mutations → boundary test suggestions with line numbers | Surviving mutations → `surviving_mutations` list with source locations; recommendations list | Both-have | N/A |
| 2.9 | **Execution gap reporting** | ERROR and INVALID_MUTATION results excluded from score denominator; reported separately in `execution_gaps` | N/A — mutflow's JUnit extension handles everything in-process; no worktree/syntax-error gaps expected | Scott-CC-only | S (adopt the `execution_gaps` concept in OMP audit) |
| 2.10 | **Quality rating formula** | Dual-factor: score AND zombie percentage thresholds | Single-factor: score only | Both-different | S to extend OMP with zombie-percentage gating |
| 2.11 | **Confidence intervals** | Statistical CI formula documented (e.g., 3/15 → 5–45% 95% CI) | Not present | Scott-CC-only | S to add CI computation to parser module |

---

## 3. Test Refactoring

| # | Feature | Scott-CC (test-refactor-specialist) | OMP (test-refactor-specialist) | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 3.1 | **Output type** | Production-ready refactored test code (full file content) | Suggestions only — no code auto-generation | Scott-CC-only | L (implement code generator + apply mechanism) |
| 3.2 | **Auto-apply** | ✅ With `--auto-approve` flag (skips second confirmation for refactoring) | ❌ No auto-apply — agent proposes, user applies manually | Scott-CC-only | M (add apply mechanism to OMP test-refactor-specialist) |
| 3.3 | **Refactoring actions** | Consolidate → parameterized tests, remove zombies, add edge case tests, replace over-mocked with integration tests | Same categories of suggestions (consolidate, remove zombies, add edge cases) but no code generation | Both-have (Scott-CC auto-generates; OMP suggests) | Gap is generation, not categories |
| 3.4 | **Diff generation** | Full git diff showing deletions, consolidations, additions | No diff generation (suggestions only) | Scott-CC-only | S (add to OMP agent + Gradle task) |
| 3.5 | **Metrics estimation** | Before/after test count, estimated mutation score, estimated speedup (time reduction) | No metrics estimation | Scott-CC-only | M (add estimation formulas to OMP agent) |
| 3.6 | **Framework-specific patterns** | pytest, unittest, Jest/Vitest parameterization patterns all documented | Kotlin-specific (JUnit 5 `@ParameterizedTest`, kotest, etc.) | Both-have (language-specific) | N/A |
| 3.7 | **User approval gate** | AskUserQuestion for approval before deleting/removing tests | Agent proposes, user manually applies — implicit approval gate via manual step | Both-have (different mechanism) | N/A |

---

## 4. Interface

| # | Feature | Scott-CC | OMP | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 4.1 | **Entry point** | `/mutation-test` slash command (Claude Code) + skill `/mutation-test` | `/mutation-test` skill → spawns `test-quality-reviewer` via `task` | Both-have | N/A |
| 4.2 | **Natural language triggers** | Auto-detection: "mutation test", "zombie tests", "mutation score", "which tests don't actually test anything" trigger automatically; "audit test quality" asks for confirmation; vague requests don't trigger | **Not present.** Explicit path or `--targets` pattern required | Scott-CC-only | XL (implement NL trigger detection in OMP harness) |
| 4.3 | **Target specification** | File or directory path; auto-detect from conversation context or git status when omitted | Project path (default: current directory) + `--targets <test-class-pattern>` | Both-have (different UX) | N/A |
| 4.4 | **Execution modes** | `--quick` (5 mutations), `--standard` (15), `--deep` (30+) via flags | No mode flags — mutflow `maxRuns` controls; no quick/deep abstraction | Scott-CC-only | M (add mode abstraction mapping to mutflow maxRuns) |
| 4.5 | **Focus parameter** | `--focus=<area>` limits mutations to specific code area | N/A (mutflow targets by class, not area) | Scott-CC-only | M (map focus to mutflow `includeTargets`/`excludeTargets` patterns) |
| 4.6 | **Setup subcommand** | Not needed — install Claude Code plugin | `/mutation-test setup [path] [--kmp]` bootstraps `.omp/` + `buildSrc/` + Gradle config | OMP-only | N/A |
| 4.7 | **Auto-approve flag** | `--auto-approve` skips confirmation for refactoring proposals | N/A (no auto-apply in OMP) | Scott-CC-only | M (add `--auto-approve` semantics to OMP command) |
| 4.8 | **External integration** | Beads (issue tracking: `bd create`, `bd update`, `bd close` auto-update with mutation score) | Gradle task (`mutationResults`), OMP `task` tool dispatch | Both-different | Scott-CC-only (Beads-specific) |
| 4.9 | **Conflict detection** | `--quick --deep` together = hard error before dispatch | N/A (no mode flags to conflict) | Scott-CC-only | Trivial if modes added |
| 4.10 | **Output contract** | Final Test Quality Audit Report: target + mode, counts (total/evaluated/caught/survived), execution gaps, mutation score (or null), zombie/redundant findings, refactoring proposal, user apply/refuse decision | Final report: mutation score + quality band, confidence, surviving mutations, zombie candidates, over-mocked tests, refactored suggestions | Both-have (Scott-CC richer) | The OMP report could be enriched with refactoring diff/metrics |

---

## 5. Safety Features

| # | Feature | Scott-CC | OMP | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 5.1 | **Isolation mechanism** | Git worktree per mutation — mutations isolated, main working tree untouched | Compile-once meta-mutant — all variants compiled at build time, runtime selects one per run; main tree never compiled with mutations | Both-have (different mechanisms) | N/A |
| 5.2 | **Main tree integrity check** | Orchestrator runs `git status --short` before and after saboteur phase as defense-in-depth; STOP if main tree changed | Not present (compile-once means no tree mutation risk) | Scott-CC-only | N/A (not needed for OMP) |
| 5.3 | **Worktree path safety** | Saboteur must use absolute paths for Edit tool; `cd` doesn't isolate Edit; mandatory post-mutation `git status` check | N/A | N/A | N/A |
| 5.4 | **User approval for test deletion** | ✅ AskUserQuestion before deleting any tests (even zombies); `--auto-approve` skips only refactoring apply, never test deletion | N/A — no auto-deletion in OMP (suggestions only) | Both-have | N/A |
| 5.5 | **Diff before changes** | Full git diff provided before applying refactoring | N/A | Scott-CC-only | S (add diff to OMP test-refactor-specialist) |
| 5.6 | **Rollback instructions** | ✅ Provided in report | Not explicit (no changes applied) | Scott-CC-only | N/A (no-op in O/A) |
| 5.7 | **Verification mode** | N/A — mutflow fork adds `VerificationMode` (STRICT/LENIENT/DISABLED) but this is at the mutflow engine level, not the agent level. OMP's test-executor could leverage this. | **Fork:** `introduce-verification-mode-strict-lenient-and-disabled` — `@MutFlowTest(verificationMode=...)` or `MUTFLOW_VERIFICATION_MODE` env var | OMP-only (via fork) | S (cherry-pick fork branch; wire into OMP skill) |
| 5.8 | **CLI safe-guard verification** | N/A — git worktrees provide isolation | **Fork:** `optional-extra-cli-safe-guard-verification` — `scripts/mutflow-verify-jar.sh` fails if a production JAR contains mutflow mutations; guarantees mutated binaries never reach production | OMP-only (via fork) | S (cherry-pick fork scripts) |
| 5.9 | **Partial run detection** | N/A | **Upstream mutflow feature:** auto-skips mutation testing when running single test method from IDE (prevents false positives from incomplete test suite) | OMP-only | N/A |
| 5.10 | **Timeout handling** | Implicit — worktree isolation means a hung test hangs one executor; no per-mutation timeout documented | mutflow's internal 60s timeout per mutation run; OMP 15-min backstop timeout | Both-have (OMP more structured) | N/A |
| 5.11 | **Worktree cleanup** | Mandatory cleanup of git worktrees after analysis (even on error) | N/A (no worktrees) | Scott-CC-only | N/A |

---

## 6. Parallelization

| # | Feature | Scott-CC | OMP | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 6.1 | **Executor model** | One `test-executor` agent per mutation, all launched in single message (N parallel) | One `test-executor` per test class (not per mutation); all test classes launched in `tasks[]` batch | Both-different | N/A |
| 6.2 | **Theoretical speedup** | 15x (15 parallel worktrees) | Serialized by mutflow's global lock — parallel executors block-and-wait; speedup = N test classes concurrent but mutations within each class run sequentially | Both-have (Scott-CC faster) | Architectural — inherent to mutflow's compile-once model |
| 6.3 | **Concurrency mechanism** | Git worktree per mutation — no shared state, no race conditions | Compiled once, runtime selects one active mutation per run via `synchronized(lock)` in `MutationRegistry.withSession()` | Both-have (different) | N/A |
| 6.4 | **Concurrency limit** | Bounded by OMP's 32-agent semaphore for executor launch | Same 32-agent semaphore; mutflow's global lock serializes mutation runs within each executor | Both-have | N/A |
| 6.5 | **Multi-run model** | Not needed — each mutation has its own worktree with clean code | Baseline (run 0) + N mutation runs (run 1+) — JUnit 6 `ClassTemplateInvocationContextProvider` handles multi-run internally | Both-have (different) | N/A |
| 6.6 | **Incremental mode** | `--focus=<area>` limits mutations to specific code area | `includeTargets`/`excludeTargets` Gradle config (via fork `add-option-to-define-mutation-targets-via-gradle-config`) limits mutation scope by class FQN patterns | Both-have (different granularity) | N/A |

---

## 7. Result Parsing

| # | Feature | Scott-CC | OMP | Gap direction | Effort to close |
|---|---|---|---|---|---|
| 7.1 | **Parsing approach** | Console output parsing — test-executor returns structured JSON per mutation; auditor parses executor results in-memory (no file I/O) | Custom Gradle task (`mutationResults`) parses JUnit XML `<system-out>` elements containing mutflow's `MutationTestingSummary` console output | Both-have (different) | N/A |
| 7.2 | **Output format** | In-memory JSON handoff between agents (no artifact persistence in Scott-CC docs) | Typed Kotlin JSON artifact at `build/reports/mutation-results.json` — structured, versioned, backward-compatible | OMP-only | N/A |
| 7.3 | **Typed data model** | No — ad-hoc JSON dicts per agent with documented schema | `@Serializable` data classes: `MutationResults`, `MutationResult`, `QualityBand`, `ConfidenceLevel`, `MutationResultType` in `buildSrc/src/main/kotlin/io/omp/mutation/` | OMP-only | N/A |
| 7.4 | **Parser location** | In-memory Python dict parsing in test-auditor agent | Pure Kotlin functions in `MutationResultsParser` object (no Gradle dependency) — unit-tested independently | OMP-only | N/A |
| 7.5 | **Unit tests** | N/A (console parsing in agent) | 18 unit tests across `MutationResultsParserTest`, `MutationStatsTest`, `MutationResultsSerializerTest` — covers multi-killer parsing, survived/timed-out, empty input, malformed lines, box-drawing char stripping, serialization round-trip | OMP-only | N/A |
| 7.6 | **Backward compatibility** | N/A | `encodeDefaults = true` ensures all fields present (including `killedByTest = null`, `killedByTests = []` for survived); field names match original string-template output exactly | OMP-only | N/A |
| 7.7 | **Multi-killer support in JSON** | `killedByTests` as array per mutation | `killedByTest` (legacy first-killer, `String?`) + `killedByTests` (full set, `List<String>`) — backward compatible with both old and new consumers | Both-have | N/A |
| 7.8 | **Box-drawing character handling** | N/A (pytest/Jest output) | Parser strips Unicode box-drawing chars (`\u2500`–`\u257F`) from mutflow's `║`/`─` formatted summary table | OMP-only | N/A |
| 7.9 | **BuildSrc module** | N/A | Typed module in `buildSrc/` with `kotlin-dsl` + `kotlinx-serialization` plugins — `.gradle.kts` scripts can't use `@Serializable` directly, so code lives in buildSrc | OMP-only | N/A |

---

## 8. Mutflow Fork Branches That Bridge Gaps

**Source:** `trancee/mutflow-exception-swap` — 12 branches inspected via `git diff origin/master..origin/<branch>`. All branches diverge from a pre-master baseline (none are direct descendants of the current `master`); the fork chain is:

```
master → feature/exception-type-swap → feature/zombie-detection → avoid-mutating-null-checks
  → double-arithmetic-ir-when-truncate-fix → optional-extra-cli-safe-guard-verification
  → introduce-verification-mode-… → add-option-to-define-mutation-targets-via-gradle-config
  → add-pipeline → update-versions
kotlin-native (independent KMP branch)
hint-for-gradle-and-jooq-user (doc-only hint)
```

| # | Branch | Commit | Files changed | What it adds | Scott-CC gap bridged | Upstreamability |
|---|---|---|---|---|---|---|
| 8.1 | `feature/exception-type-swap` | b5e94… | 6 files (+`.scratch/pr16-review-comments.md`) | `ExceptionTypeSwapOperator` — new `ConstructorMutationOperator` that swaps thrown exception types (e.g., `IllegalArgumentException` → `IllegalStateException`) via `visitThrow` + `MutationRegistry.check()`. Uses `kotlin.*` FQNs for cross-platform portability. Handles `visitThrow` (not just `IrConstructorCall`). Includes detailed review-comment notes on API widening. | **Strategy 5: Exception types** — OMP had no exception-type mutation operator | ✅ Yes — bug fix for missing strategy; PR #16 already open upstream |
| 8.2 | `feature/zombie-detection` | 7c504… | 2 files | Changes `MutationResult.Killed(testName: String)` → `Killed(testNames: Set<String>)` capturing ALL killing tests. Removes `!testFailedInCurrentRun` guard in `markTestFailed()` so multiple killers are tracked. `printSummary()` emits multiple `killed by:` lines per mutation. **Critical:** stores `killedByTests.toSet()` (immutable copy) before `clear()` to avoid wiping stored results. | **Full per-test-per-mutation matrix** — Scott-CC's auditor needs all killers per mutation for precise zombie detection | ✅ Yes — enhancement, clean separation |
| 8.3 | `avoid-mutating-null-checks` | 7446b… | 4 files | `EqualitySwapOperator.matches()` skips `==` and `!=` when either operand is `null` literal. Prevents confusing mutations on Kotlin `?.`/`?:` desugared null checks; skips explicit `x == null` / `x != null` as equivalent mutants. | **Mutation quality** — reduces noise from equivalent/confusing mutants | ✅ Yes — bug fix for false positives |
| 8.4 | `double-arithmetic-ir-when-truncate-fix` | ef5f5… | 4 files | Fixes `IrWhenImpl` hardcoded to `booleanType` → uses `original.type`. Double-precision arithmetic lost fractional precision (e.g., `50.0 * 0.05` → `2.0` instead of `2.5`). | **Correctness bug** in arithmetic operator — affects Strategy 4 accuracy | ✅ Yes — clear bug fix |
| 8.5 | `introduce-verification-mode-strict-lenient-and-disabled` | d4c0e… | 5 files | Adds `VerificationMode` enum (STRICT/LENIENT/DISABLED) to `@MutFlowTest`. STRICT (default): survivors fail build. LENIENT: survivors reported but don't fail. DISABLED: mutation runs skipped entirely. `MUTFLOW_VERIFICATION_MODE` env var overrides annotation. | **Verification modes** — Scott-CC has user approval gates; mutflow provides engine-level control over survivor handling | ✅ Yes — broadly useful feature |
| 8.6 | `add-option-to-define-mutation-targets-via-gradle-config` | 6dd82… | 8 files | Adds glob-style `targets` property to Gradle DSL: `includeTargets`/`excludeTargets` with `*` (single segment) and `**` (multi-segment) wildcards. Compiles to regex, checks class FQN in `MutflowIrTransformer.visitClass`. | **Interface gap** — Gradle-based target scoping (maps to Scott-CC's `--focus` concept) | ✅ Yes — useful configuration option |
| 8.7 | `optional-extra-cli-safe-guard-verification` | e22c1… | 2 files | `scripts/mutflow-verify-jar.sh` — CLI guard that fails if a production artifact JAR contains mutflow mutations. Guarantees mutated binaries never reach production. | **CLI guard** — Scott-CC's safety features (worktree isolation, rollback) vs mutflow's artifact verification | ✅ Yes — safety tooling |
| 8.8 | `add-pipeline` | 5a032… | 1 file (`.github/workflows/ci.yml`) | Basic CI pipeline: JDK 17 setup + `./gradlew build` on PRs to master | **Interface gap** — CI integration (Scott-CC mentions CI can be added) | ⚠️ Minimal — might need more comprehensive pipeline |
| 8.9 | `update-versions` | 63a3d… | 6 files | Kotlin/Gradle/Gradle-wrapper version bumps (2.4.0) | Maintenance only | ❌ No — routine updates |
| 8.10 | `hint-for-gradle-and-jooq-user` | cb122… | 1 file (README) | Documentation hint: `tasks.withType<KotlinCompile> { dependsOn("jooqCodegen") }` to fix mutflow+JOQQ codegen dependency | Documentation only | ❌ No — doc hint |
| 8.11 | `kotlin-native` | e842b… | 46 files | Experimental Kotlin/Native support: KMP modules, per-target instrumented compilations, `mutflow<Target>Test` tasks, env-var/file mutation selection contract. Native klibs stay un-instrumented | **KMP expansion** — OMP is JVM-first; this is an OMP-only feature not shared with Scott-CC | ⚠️ Experimental — needs stabilization |
| 8.12 | (none) | — | — | No 12th unique feature branch — the 12 branches include master. The `feature/exception-type-swap` and `exception-type-swap` from the issue ticket are the same branch (issue had a typo). | N/A | N/A |

### Fork branch → Scott-CC gap bridging summary

| Fork branch | Closes gap in dimension | OMP status |
|---|---|---|
| `feature/exception-type-swap` | Strategy 5 (Exception types) | ✅ Fork already integrated into OMP `.omp/` (test-saboteur agent notes ExceptionTypeSwapOperator, domain-model row 1.5) |
| `feature/zombie-detection` | Quality 2.4/2.5 (multi-killer zombie detection) | ✅ Fork already integrated (mutation-results.gradle.kts parses multiple `killed by:` lines; MutationResultsSerializerTest verifies `killedByTests` array) |
| `double-arithmetic-ir-when-truncate-fix` | Strategy 4 correctness | ⚠️ Bug fix — should be cherry-picked for correctness |
| `avoid-mutating-null-checks` | Mutation quality (noise reduction) | ⚠️ Not yet in OMP `.omp/` configs — should cherry-pick |
| `introduce-verification-mode-…` | Safety 5.7 (verification modes) | ⚠️ Not wired into OMP skill — should cherry-pick |
| `add-option-to-define-mutation-targets-via-gradle-config` | Interface 4.5/6.6 (target scoping) | ⚠️ Not wired into OMP skill — should cherry-pick |
| `optional-extra-cli-safe-guard-verification` | Safety 5.8 (CLI guard) | ⚠️ Not in OMP — should cherry-pick scripts |
| `add-pipeline` | Interface (CI) | ❌ Basic CI only — Scott-CC doesn't have CI pipeline either |
| `kotlin-native` | KMP expansion | ❌ Out of Scott-CC scope (Python/JS); OMP-only future feature |

---

## Summary: Gap Inventory

### Scott-CC features OMP lacks (residual gaps after fork)

| Gap | Dimension | Fork bridge available? | Effort estimate |
|---|---|---|---|
| No redundant test group detection (tests that always fail together → consolidate) | Quality 2.6 | ❌ No fork branch | L (new algorithm in test-auditor) |
| No auto-generated refactored test code; suggestions only | Refactoring 3.1 | ❌ No fork branch | L (code generator + apply mechanism) |
| No `--auto-approve` for auto-apply | Refactoring 3.2 | ❌ No fork branch | M |
| No natural language triggers (auto-detection) | Interface 4.2 | ❌ No fork branch | XL (NL trigger detection in harness) |
| No quick/standard/deep mode abstraction | Interface 4.4 | ❌ No fork branch | M |
| No `--focus=<area>` parameter | Interface 4.5 | ⚠️ Partial: `add-option-to-define-mutation-targets-via-gradle-config` provides class-level scoping via Gradle DSL | S (bridge to OMP skill CLI) |
| No diff generation before applying refactoring | Safety 5.5 | ❌ No fork branch | S |
| No explicit rollback instructions | Safety 5.6 | ❌ No fork branch | Trivial |
| No confidence intervals (statistical CI) | Quality 2.11 | ❌ No fork branch | S |
| No execution gap reporting (ERROR/INVALID_MUTATION) | Quality 2.9 | ❌ No fork branch (mutflow doesn't produce these) | S |

### OMP features Scott-CC lacks

| Gap | Dimension | Notes |
|---|---|---|
| Confidence levels (Low/Medium/High by mutation count) | Quality 2.3 | Not in Scott-CC — OMP is superior here |
| Typed Kotlin JSON result parsing module with 18 unit tests | Result parsing 7.3–7.5 | Scott-CC uses in-memory dict handoff; OMP has robust typed module |
| Verification modes (STRICT/LENIENT/DISABLED) | Safety 5.7 | Fork bridge; OMP-only |
| CLI safe-guard script (artifact verification) | Safety 5.8 | Fork bridge; OMP-only |
| Partial run detection (IDE single-test safety) | Safety 5.9 | Upstream mutflow; OMP-only |
| Setup subcommand (`/mutation-test setup`) | Interface 4.6 | Bootstrap into new projects |

### Gap direction distribution

| Gap direction | Count |
|---|---|
| Both-have | 8 rows |
| Both-different | 7 rows |
| Scott-CC-only | 10 rows |
| OMP-only | 5 rows |
| N/A (not applicable / architectural) | 8 rows |

**Net:** OMP has 5 features Scott-CC lacks; Scott-CC has 10 features OMP lacks (4 of which are partially bridgeable via fork branches: exception types, multi-killer zombie detection, verification modes, Gradle target scoping, CLI safe-guard).
