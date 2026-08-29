# Context: Mutation testing agent system

## Overview

A 5-agent mutation testing system for Kotlin (JVM-first) projects, built on OMP's agent/task/skill architecture and powered by [mutflow](https://github.com/anschnapp/mutflow) as the underlying mutation engine.

The system uses mutflow's compile-once engine and predefined operators. Agents select targets, execute tests, calculate quality metrics, and propose test improvements. They do not generate mutation operators.

## Key concepts

### Mutation testing

Injecting small faults (mutations) into source code and running tests to see whether they catch the faults. The mutation score is `killed / (total - gaps)`. A higher score means the tests detected a larger share of evaluated mutations. The score is null when no mutations are evaluable.

### Meta-mutant (mutflow)

mutflow injects all mutation variants into the compiled code, guarded by conditional branches with `MutationRegistry.check()` calls. At runtime, one variant is active per test run. This compile-once approach avoids per-mutation recompilation.

### Zombie test

A test that passes even when the code is mutated. In Scott-CC's model, a zombie test passes for every mutation. In this system, a zombie candidate is a test that never appears in the `testKillerMatrix` for any killed mutation. It executes during mutation runs but never kills a mutation. mutflow records every test that catches each mutation, not only the first.

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
| Exception types | ExceptionTypeSwapOperator | Full |
| Zombie detection | Per-test-per-mutation matrix | Full |

## Data contracts

The `mutationResults` Gradle task outputs `mutation-results.json` including `killedByTests` (all killing tests per mutation) and `testKillerMatrix` (test → mutation source locations). The format and quality bands are documented in the [mutation results reference](docs/reference/mutation-results-format.md).

## Decisions deferred to v2

- Kotlin/JS and Kotlin/Native mutation targets. Kotlin Multiplatform setup currently covers JVM source sets only.

## References

- [mutflow](https://github.com/anschnapp/mutflow)
- [ADR-001: Use mutflow as the mutation engine](docs/adr/0001-use-mutflow-as-mutation-engine.md)
- [ADR-002: Agent structure and orchestration](docs/adr/0002-agent-structure-and-orchestration-model.md)
- `.scratch/omp-mutation-testing/map.md`, the Wayfinder map
