# A mutation-testing agent system in OMP that ports Scott-CC's multi-agent orchestration and LLM-guided semantic mutations to Kotlin (JVM-first), powered by mutflow as the underlying mutation engine.

Labels: wayfinder:map

## Destination

**Port Scott-CC's mutation-testing plugin to OMP as a 5-agent system for Kotlin (JVM-first), using mutflow as the mutation engine.**

Reaches from here when: there exists an `/mutation-test` skill in OMP that dispatches to domain-specific subagents (test-quality-reviewer, test-saboteur, test-executor×N, test-auditor, test-refactor-specialist), uses mutflow for mutation injection, runs tests in parallel, calculates mutation score with zombie detection, and proposes refactored Kotlin test suites — all orchestrated through OMP's `task` tool rather than Claude Code's `Task`.

## Notes

**Scope decisions (from destination grilling):**
- Engine: mutflow (Kotlin compiler plugin, compile-once meta-mutant, JUnit 6 native)
- Architecture: Port Scott-CC's 5-agent pipeline to OMP's agent/task system
- KMP targets: JVM first, JS/Native/Android expansion later
- Issue tracker: local markdown (`.scratch/`)

**Skills to consult every session:**
- /research — for fact-finding tickets (R1, R2)
- /grilling — for decision tickets that require human input (D1–D4)
- /domain-modeling — to maintain glossary and ADRs as decisions crystallize
- /prototype — for concrete artifacts when "how should it behave" is the question

**Conventions:**
- Refer to tickets by name, not number
- Research tickets resolve via /research subagents (AFK)
- Decision tickets resolve via grilling sessions (HITL)
- One ticket per session (except research tickets, which run in parallel)
- Charting this map resolved nothing — only created the frontier

## Decisions so far

### R1 resolved — OMP agent dispatch
- Agents are markdown files with YAML frontmatter in `.omp/agents/` (project) or `~/.omp/agent/agents/` (user)
- `name` frontmatter field is the dispatch key for the `task` tool's `agent` parameter (case-sensitive, no namespace needed)
- Tools declared per-agent via `tools` frontmatter; `hub` retained for coordination; `task`/`yield` auto-added
- Parallel execution: `tasks[]` batch (bounded by semaphore, default 32)
- Skills are prompt-driven, not tool-restricted; `/skill:<name> [args]` for invocation
- **Implication**: Direct port of Scott-CC's 5-agent system is possible — create 5 agent files, orchestrator uses `task` tool for dispatch

### R2 resolved — mutflow architecture
- **JVM-only**: No KMP support. Gradle plugin checks `org.jetbrains.kotlin.jvm` only; dual-compilation creates `mutatedMain` source set from `main`
- **Compile-once meta-mutant**: All mutations injected as IR branches with `MutationRegistry.check()` calls at compile time; runtime selects one per run
- **Static operator catalog only**: 5 call operators, 2 return operators, 1 function body, 1 when operator — no LLM-guided extension point
- **Serialized mutation runs**: `synchronized(lock)` in `MutationRegistry.withSession()` — only one mutation session active at a time
- **Zombie detection**: `MutationResult.Killed(testNames: Set<String>)` captures ALL tests that kill each mutation (via the fork); `Survived` for zombie mutations; `testKillerMatrix` provides full per-test-per-mutation mapping
- **JUnit 6 extension**: `@MutFlowTest` + `MutFlowExtension` uses `ClassTemplateInvocationContextProvider` to run baseline (run 0) then one mutation per run (run 1+)
- **Implication**: mutflow's compile-once approach replaces Scott-CC's per-mutant git worktrees — no git worktree needed, with full per-test-per-mutation zombie detection via the fork

### D1 resolved — Agent structure (grilling 2026-08-22)
- **5 separate agent files** in `.omp/agents/`: test-quality-reviewer (orchestrator), test-saboteur, test-executor, test-auditor, test-refactor-specialist
- **Project-level location** (`.omp/agents/`) — version-controlled, portable
- **Sequential handshake** — saboteur configures mutations → executors run tests → auditor analyzes. Fits mutflow's baseline-before-mutation run ordering
- **Restricted tool permissions** per agent — executor can't edit, saboteur can't spawn subagents, auditor is read-only+parse
- **Thin skill** (`.omp/skills/mutation-test/SKILL.md`) → spawns test-quality-reviewer via `task`
- **mutflow adaptation**: saboteur configures `@MutFlowTest` (no git worktrees); executor runs `./gradlew test` (JUnit extension handles multi-run); one executor per test class (mutflow's global lock serializes mutations)

- **Custom Gradle task for result capture**: Structured JSON output (pointId, variantIndex, result, killedByTests array, testKillerMatrix), cross-referenced with JUnit XML for per-test metadata. Enables auditor to build mutation score + zombie detection data
- **Fixed 15-min OMP timeout**: Backstop for mutflow's 60s internal timeout. Simple, no pre-run baseline calculation needed
- **Executor runs per test class**: Not per mutation. mutflow's JUnit extension handles multi-run internally (baseline + N mutation runs)

### D3 implication for D4 (zombie detection)
- Custom Gradle task JSON capture provides per-mutation results with `killedByTests` array — ALL tests that catch each mutation
- `testKillerMatrix` maps test display names → mutation source locations killed. Enables precise zombie detection without forking mutflow

### D2 resolved — Semantic mutations (grilling 2026-08-22)
- **mutflow operators as-is + LLM suppression targeting**: No separate mutation injection. Saboteur adds `@MutationTarget` to business-logic classes, `// mutflow:ignore` to framework code
- **Pre-select targets via LLM**: LLM proactively identifies business logic vs framework noise before mutflow runs
- **4 of 5 Scott-CC strategies map to mutflow**: Boundary (RelationalComparison + ConstantBoundary), Return Values (BooleanReturn + NullableReturn), Boolean Logic (BooleanInversion + EqualitySwap + BooleanLogic), Arithmetic (ArithmeticOperator). Exception types now covered via ExceptionTypeSwapOperator (upstream PR #16)
- **Saboteur role redefined**: Targeting specialist, not mutation creator. mutflow's compiler plugin injects all mutations at compile time

### D2 implication for not-yet-specified
- "Whether to use mutflow's operators exclusively or augment with LLM" → resolved: as-is + LLM suppression targeting
- Exception type mutations now covered (ExceptionTypeSwapOperator implemented, upstream PR #16 open)

### D4 resolved — Zombie detection
- **Full per-test-per-mutation zombie detection**: mutflow fork tracks ALL tests that kill each mutation (via `Set<String>` in `markTestFailed`). The `mutation-results.gradle.kts` task builds `testKillerMatrix` mapping each test → mutation source locations killed. Zombie candidates = tests that never appear in the matrix.
- **Quality bands with mutation-count confidence**: Score = killed / total. Excellent >80%, Good 60-80%, Fair 30-60%, Poor <30%. Confidence: <10 mutations = low, 10-50 = medium, 50+ = high
- **Over-mocking detection**: Count MockK `mockk()`/`spyk()`/`@MockK` and Mockito `mock()`/`@Mock` per test method. Flag >3 mocks as candidates
- **Custom Gradle task JSON** (from D3): `killedByTests` array + `testKillerMatrix` — no console parsing needed

## Not yet specified (open gaps)

- None — all original v1 gaps resolved

## All decision tickets resolved

- R1 (OMP agent dispatch) → resolved
- R2 (mutflow architecture) → resolved
- D1 (agent structure) → resolved: 5 agents in `.omp/agents/`, sequential handshake, restricted tools, thin skill
- D2 (semantic mutations) → resolved: mutflow operators as-is + LLM suppression targeting; 4/5 strategies map; exception types covered via ExceptionTypeSwapOperator (upstream PR #16)
- D3 (parallelization) → resolved: parallel batch, custom Gradle task + JUnit XML, 15-min fixed timeout
- D4 (zombie detection) → resolved: full per-test-per-mutation matrix via mutflow fork (tracks all killers), testKillerMatrix in JSON, precise zombie candidate identification

## Out of scope

- pitest/Arcmutate engine — rejected in favor of mutflow (Decision: Engine preference)
- Python/JS/TS mutation testing — Scott-CC's original scope; this effort is Kotlin-only
- JS/Native/Android KMP targets in initial implementation — JVM-first, expansion is a separate effort
- Building a new mutation engine from scratch — leveraging mutflow as the engine
