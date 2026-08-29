# About mutflow's compile-once meta-mutant architecture

mutflow's compile-once meta-mutant approach differs from mutation engines that compile the source separately for each mutation.

## The traditional approach

Most mutation testing engines work by compiling the source code many times — once per mutation. For N mutations, you need N compilations and N test runs. Scott-CC's original plugin followed this model: it created a **git worktree per mutation** and ran tests in parallel across all of them.

## mutflow's approach

mutflow injects **all mutation variants** into the compiled code at **compile time**, guarded by conditional branches with `MutationRegistry.check()` calls. At runtime, one variant is activated per test run. This is a "compile-once" approach — no per-mutation recompilation is needed.

```
Source code
    │
    ▼
┌────────────────────────────┐
│  Mutflow IrTransformer     │  Injects check() calls at every
│  (Kotlin compiler plugin)  │  IR node for all operators
└────────────────────────────┘
    │
    ▼
    Mutated bytecode (all variants, guarded)
    │
    ▼
    Test run 0: baseline (no active mutation)
    │
    ▼
    Test run 1: activate mutation #1 → run tests
    │
    ▼
    Test run 2: activate mutation #2 → run tests
    ...
```

### Key implications

1. **Single compilation**: All mutations compile in one pass. This removes per-mutation compilation and git worktree management.

2. **Per-JVM synchronized lock**: `MutationRegistry.withSession()` uses `synchronized(lock)`, so one mutation session runs at a time inside each JVM. Each test class still runs its baseline before its mutation variants.

3. **Runtime selection**: The `MutationRegistry.check()` calls are no-ops during baseline execution (null active mutation). During mutation runs, one `ActiveMutation` is activated and all others are skipped.

4. **JUnit 6 integration**: The `@MutFlowTest` annotation uses JUnit 6's `ClassTemplateInvocationContextProvider` to orchestrate baseline (run 0) + mutation runs (run 1+). `MutFlow.underTest { }` blocks wrap business logic calls for mutation injection.

5. **Aggregate verdicts**: mutflow records `Killed(testNames: Set<String>)`, `Survived`, and `TimedOut` outcomes. The `mutation-results.gradle.kts` task builds a `testKillerMatrix` that maps each test to the mutation source locations it killed. Tests that execute but never kill a mutation become zombie-test candidates.

## Why this matters for the OMP agent system

- **No git worktrees**: The saboteur doesn't need to create worktrees per mutation — mutflow handles isolation via compile-once.
- **One executor per test class**: Not per mutation. Each test class with `@MutFlowTest` runs all its mutations in one `./gradlew test` invocation.
- **Stdout capture**: mutflow prints `MutationTestingSummary` to stdout. The `mutation-results.gradle.kts` task captures this from JUnit XML's `<system-out>` elements (not from Gradle's `StandardOutputListener`, which is unavailable in Kotlin DSL on Gradle 9.x).
- **Full per-test-per-mutation zombie detection**: mutflow's `MutFlowSession.markTestFailed()` now tracks ALL tests that kill each mutation (via a `Set<String>`). The parser extracts all "killed by:" lines and builds `testKillerMatrix` for precise zombie candidate identification. Included in upstream mutflow v1.1.1+.
