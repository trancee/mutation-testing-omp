# Design parallel test execution strategy for OMP

Labels: wayfinder:task
Type: task
Status: resolved (grilling session 2026-08-22)
Blocked by: (resolved — R1 research complete)

## Question

How to parallelize test execution across mutations in OMP, given that mutflow uses a compile-once meta-mutant model rather than Scott-CC's per-mutant git worktree model?

Scott-CC launches 15 test-executor agents in parallel (one per mutation worktree). But mutflow injects all mutations into a single compilation with runtime selection — no worktrees needed. This changes the parallelization model:

1. Does mutflow's meta-mutant approach mean we run test-executor agents per-run (activating one mutation per run) rather than per-worktree? How many runs, and how to distribute them?
2. How does OMP's `task` tool express "launch N subagents in one message for parallel execution"? What's the batch limit?
3. Does mutflow's `@MutFlowTest` handle the multi-run orchestration internally (JUnit extension), or do we need to drive runs externally? If internally, what's the role of test-executor agents?
4. How to aggregate per-test pass/fail outcomes across runs for zombie detection (the auditor needs `test_outcomes` per mutation)?
5. Does mutflow's timeout detection for infinite-loop mutations (e.g., flipping `<` in a loop condition) interact with OMP's task timeout system?
## Resolution

### Decisions (from grilling 2026-08-22)
1. **Parallel batch execution**: All test-executor agents dispatched in one `tasks[]` batch. mutflow's global synchronized lock serializes mutation runs internally. Non-mutated test classes run in parallel; @MutFlowTest classes block-and-wait on the lock. OMP's 32-agent semaphore cap is sufficient for any realistic test suite.
2. **Custom Gradle task for result capture**: Write a Gradle task that runs mutflow tests and outputs structured JSON with fields: `pointId`, `variantIndex`, `result` (Killed/Survived/TimedOut), `killedByTests` (array of ALL tests that caught each mutation), `testKillerMatrix` (test → mutation source locations). Cross-reference with JUnit XML for test-level metadata.
3. **Fixed 15-min OMP timeout per test class**: Simple, generous. Covers baseline + all mutation runs (60s mutflow timeout per run + overhead). Avoids complex pre-run baseline calculation.

### Answering the 5 ticket questions

| Q# | Answer |
|---|---|
| 1 | Executor runs per test class (not per mutation). mutflow's JUnit extension handles per-mutation multi-run internally (baseline + N mutation runs per class). |
| 2 | `tasks[]` batch in one `task` call. Semaphore cap = 32. All executor agents dispatched simultaneously. |
| 3 | @MutFlowTest handles multi-run internally via ClassTemplateInvocationContextProvider (baseline run 0 + mutation runs 1+). Executor's role: invoke `./gradlew test`, capture stdout + JSON output + JUnit XML. |
| 4 | Custom Gradle task outputs structured JSON (pointId, variantIndex, result, killedByTests array, testKillerMatrix). JUnit XML provides per-test names. Auditor cross-references to build mutation score + zombie detection data. |
| 5 | mutflow injects `checkTimeout()` at loop bodies, throws `MutationTimedOutException` (60s default). OMP's 15-min timeout is a backstop — if mutflow's detection fails, OMP will eventually cancel the hung task. |

