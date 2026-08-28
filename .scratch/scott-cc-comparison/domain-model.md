# Domain Model: Mutation Testing Systems

## Core Entities

### Mutation Engine
The tool that generates mutations in source code.

| Implementation | Scott-CC | OMP |
|---|---|---|
| Engine type | LLM-guided semantic mutation | `mutflow` — Kotlin compiler plugin (IR transformer) |
| Mutation generation | Agent reads source, decides what to mutate | Compile-time IR branch injection (`MutationRegistry.check()`) |
| Operator catalog | 5 strategies chosen per-file by LLM | Predefined operators (RelationalComparison, BooleanReturn, etc.) |

### Mutation Strategy
A category of realistic bug pattern.

| # | Strategy | Scott-CC | OMP/mutflow | Fork bridge |
|---|---|---|---|---|
| 1 | Boundary conditions | `>=` → `>`, `==`, `<=` | `RelationalComparisonOperator`, `ConstantBoundaryOperator` | ✅ |
| 2 | Return values | `return x` → `return None/""/` | `BooleanReturnOperator`, `NullableReturnOperator` | ✅ |
| 3 | Boolean logic | `and` → `or`, negate | `BooleanInversionOperator`, `EqualitySwapOperator`, `BooleanLogicOperator` | ✅ |
| 4 | Arithmetic operators | `*` → `/`, `+`, `-` | `ArithmeticOperator` | ✅ (+ IR truncate fix) |
| 5 | Exception types | `raise ValueError` → `TypeError` | NO operator | ✅ via `ExceptionTypeSwapOperator` |

### Test Isolation
How mutations are kept separate from the main working tree.

| Approach | Scott-CC | OMP |
|---|---|---|
| Mechanism | Git worktree per mutation | Compile-once meta-mutant (all variants compiled, one active per run) |
| Tradeoff | Full parallelization possible | Global synchronized lock serializes runs |
| Safety | Main tree never touched | Compile-time only; main tree untouched |

### Test Executor
The component that runs tests against mutated code.

| Aspect | Scott-CC | OMP |
|---|---|---|
| Parallelism | 15 parallel agents (Nx speedup) | One per test class; mutflow serializes mutations within |
| Execution | `pytest` / `npm test` in worktree | `./gradlew test` (JUnit 6 extension) |
| Multi-run model | Not needed (separate worktrees per mutation) | Baseline (run 0) + N mutation runs (run 1+) |

### Quality Analyzer
The component that interprets test results to assess test quality.

| Feature | Scott-CC (test-auditor) | OMP (test-auditor + Gradle task) |
|---|---|---|
| Mutation score | killed / total | killed / total |
| Quality bands | >80% Excellent, >60% Good, >40% Fair | >80% Excellent, >60% Good, >30% Fair, <30% Poor |
| Confidence | Not tracked | Low (<10), Medium (10-50), High (50+) |
| Zombie detection | Tests never failed across all mutations | Per-test-per-mutation matrix (all killer tests tracked) |
| Redundant groups | Tests that always fail together (>5 in same group) | ❌ Not present |
| Over-mocked detection | >5 mocks per test | >3 mocks per test (MockK + Mockito) |
| Missing coverage | Surviving mutations → suggestions | Surviving mutations → recommendations |

### Test Refactoring
The component that proposes or generates improved test code.

| Aspect | Scott-CC (test-refactor-specialist) | OMP |
|---|---|---|
| Output | Production-ready refactored test code | Suggestions only (no auto-apply) |
| Actions | Consolidate, remove zombies, add edge cases, replace over-mocked | Same suggestions |
| User involvement | Approval before applying | Agent proposes, user applies manually |
| Auto-apply | ✅ With `--auto-approve` | ❌ |

### Interface
How the user invokes the system.

| Aspect | Scott-CC | OMP |
|---|---|---|
| Entry point | `/mutation-test` slash command | `/mutation-test` skill → `task` dispatch |
| Auto-detection | Natural language triggers ("mutation test", "zombie tests") | Not present (explicit path required) |
| Modes | `--quick` (5), standard (15), `--deep` (30+) | N/A (mutflow controls mutation count) |
| Setup | Not needed (install plugin) | `/mutation-test setup` (bootstraps .omp/ files + buildSrc) |
| External integration | Beads (issue tracking) | OMP task tool, Gradle |

## ADRs Referenced
- ADR-001: Engine selection — mutflow for Kotlin (compile-once), LLM for Python/JS
- ADR-002: Isolation strategy — compile-once meta-mutant eliminates git worktrees
- ADR-003: Result format — typed Kotlin JSON module in buildSrc (vs console parsing)

## Key Terminology
- **Zombie test**: A test that passes even when the code is mutated (doesn't catch bugs)
- **Mutation score**: % of mutations caught by the test suite
- **Survived mutation**: A mutation where all tests passed (not caught)
- **Killed mutation**: A mutation where at least one test failed (caught)
- **Test killer matrix**: Maps each test → list of mutation source locations it killed
- **Meta-mutant**: All mutation variants injected at compile time, with runtime selection
