# Context: Mutation Testing Agent System

## Overview

A 5-agent mutation testing system for Kotlin (JVM-first) projects, built on OMP's agent/task/skill architecture and powered by [mutflow](https://github.com/anschnapp/mutflow) as the underlying mutation engine.

This system ports [Scott-CC's](https://github.com/Scott-CC/mutation-testing-plugin) multi-agent orchestration and LLM-guided semantic mutations to OMP, adapting from Scott-CC's per-mutant git worktree model to mutflow's compile-once meta-mutant architecture.

## Key concepts

### Mutation testing
Injecting small faults (mutations) into source code and running tests to see if they catch the faults. Metrics: mutation score = killed / total. High score = high-quality tests.

### Meta-mutant (mutflow)
mutflow injects ALL mutation variants into the compiled code at compile time, guarded by conditional branches with `MutationRegistry.check()` calls. At runtime, one variant is activated per test run. This is a "compile-once" approach — no per-mutation recompilation needed.

### Zombie test
A test that passes even when the code is broken (mutated). In Scott-CC's model, a zombie test passes for every mutation. In mutflow's model (this system), a zombie candidate is a test that never appears in the `testKillerMatrix` for any killed mutation — it executes during mutation runs but never kills any mutation. Full per-test-per-mutation tracking is enabled by the fork: mutflow records ALL tests that catch each mutation, not just the first.

### Over-mocked test
A test that uses excessive mocking (`mockk()`, `mock()`), potentially masking real logic and reducing mutation sensitivity. Flagged when a test method has >3 mock calls.

## Agent architecture

| Agent | Role |
|-------|------|
| test-quality-reviewer | Orchestrator — coordinates the pipeline via `task` tool dispatch |
| test-saboteur | Mutation targeting — adds `@MutationTarget`, `@MutFlowTest`, `// mutflow:ignore` |
| test-executor | Test execution — runs `./gradlew test`, captures stdout + JUnit XML + JSON |
| test-auditor | Results analysis — parses output, calculates score, identifies zombies |
| test-refactor-specialist | Test improvement — generates refactored test code |

## Mutation strategies

| Scott-CC Strategy | mutflow Operator | Coverage |
|---|---|---|
| Boundary conditions | RelationalComparisonOperator + ConstantBoundaryOperator | Full |
| Return values | BooleanReturnOperator + NullableReturnOperator | Full |
| Boolean logic | BooleanInversionOperator + EqualitySwapOperator + BooleanLogicOperator | Full |
| Arithmetic | ArithmeticOperator | Full |
| Exception types | ExceptionTypeSwapOperator (fork) | Implemented (PR [#16](https://github.com/anschnapp/mutflow/pull/16) — pending upstream merge) |
| Zombie detection | Per-test-per-mutation matrix (fork) | Implemented (PR [#17](https://github.com/anschnapp/mutflow/pull/17) — pending upstream merge) |

## Data contracts

The `mutationResults` Gradle task outputs `mutation-results.json` including `killedByTests` (all killing tests per mutation) and `testKillerMatrix` (test → mutation source locations). The format and quality bands are documented in the [mutation results reference](../reference/mutation-results-format.md).

## Decisions deferred to v2

- KMP/JS/Native target support (mutflow is JVM-only)

## References

- mutflow: https://github.com/anschnapp/mutflow
- ADR-001: Use mutflow as mutation engine
- ADR-002: 5-agent architecture and orchestration model
- `.scratch/mutation-testing-omp/map.md` — Wayfinder map
