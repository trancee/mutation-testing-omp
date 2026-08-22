# Adapt zombie test detection to mutflow's meta-mutant architecture

Labels: wayfinder:task
Type: task
Status: resolved (grilling session 2026-08-22)
Blocked by: (resolved — R2 research complete)

## Question

How to detect zombie tests (tests that pass despite broken code) using mutflow's compile-once meta-mutant approach, given that mutflow's architecture differs fundamentally from Scott-CC's per-worktree model?

Scott-CC detects zombies by comparing per-test outcomes across per-mutation git worktrees: a test is a zombie if it passes for every mutation. mutflow injects all mutation variants as conditional branches in one compilation, activating one per run via `MutationRegistry.check()`:

1. **Test outcome capture**: Does mutflow's `@MutFlowTest` / `MutFlow.underTest` expose per-test pass/fail outcomes per mutation run? Or only an aggregate survived/killed verdict? How to get the `test_outcomes` map that Scott-CC's auditor needs?
2. **Run model differences**: Scott-CC runs all tests per mutation (15 worktrees × all tests). mutflow runs all tests multiple times with different active mutations. How does this change which tests are "evaluated" vs "unevaluated" for zombie purposes?
3. **Partial run detection**: mutflow has "automatic partial run detection — skips mutation testing when running single tests from IDE." How does this affect zombie detection when running via OMP?
4. **Over-mocking detection**: Scott-CC's auditor reads the test file and counts `@patch` / `Mock()` / `mocker.patch()` calls. What's the Kotlin equivalent? (MockK's `mockk()`, Mockito's `mock()`, etc.)
5. **Survivor output format**: mutflow shows survivors as `MUTANT SURVIVED: (Calculator.kt:8) > → >=`. How to parse these into Scott-CC's zombie test analysis format?
## Resolution

### Decisions (from grilling 2026-08-22)
1. **Approximate zombie detection**: Auditor cross-references JUnit XML (all test method names per class) against mutflow's JSON `killedByTest` values. Tests that NEVER appear as killedByTest for any killed mutation are flagged as zombie candidates. This is imprecise (false positives for tests that don't exercise mutated code) but fast — no extra test runs needed.
2. **Standard quality bands with mutation-count confidence**: Mutation score = killed / total. Excellent >80%, Good 60-80%, Fair 30-60%, Poor <30%. Confidence: <10 mutations = low, 10-50 = medium, 50+ = high.
3. **Over-mocking detection via mock counting**: Parse test source files, count `mockk()`, `mock()`, `spyk()`, `@MockK`, `@Mock` per test method. Flag tests with >3 mocks as over-mocking candidates. LLM (test-refactor-specialist) reviews flagged tests.

### Answering the 5 ticket questions

| Q# | Answer |
|---|---|
| 1 | mutflow does NOT expose per-test-per-mutation outcomes. `markTestFailed()` only records the FIRST killer test per mutation (gated by `if (!testFailedInCurrentRun && activeMutation != null)`). JUnit XML shows all tests as "passed" during mutation runs (failures swallowed). The custom Gradle task (D3 decision) captures JSON with killedByTest + JUnit XML cross-reference for approximation. |
| 2 | Run model is test-class-level, not per-mutation. All tests in a class execute per mutation run (baseline + N mutation runs). "Evaluated" = all tests in the class for each run. "Unevaluated" = tests that ran but didn't fail (can't distinguish from zombies without per-test re-runs). |
| 3 | mutflow's partial run detection (executedTestIds check) skips mutation testing when running single tests from IDE. Via OMP, the test-executor runs the full class, so partial detection does NOT trigger. But if an executor is misconfigured to run single tests, mutation testing will be skipped silently. |
| 4 | Kotlin equivalent: MockK's `mockk()` and `spyk()`, Mockito-Kotlin's `mock()` and `mockOrNull()`. Annotations: `@MockK`, `@Mock`. Count per test method, flag >3 as candidates. |
| 5 | Custom Gradle task (D3 decision) outputs structured JSON instead of parsing console text. No need to parse `MUTANT SURVIVED: ...` — the JSON contains pointId, variantIndex, result, killedByTest directly. |

### Zombie detection data flow

1. **test-executor** runs `./gradlew mutationTest` (custom Gradle task)
2. Custom task outputs JSON: `{class, mutations: [{pointId, variantIndex, result, killedByTest}]}` + JUnit XML
3. **test-auditor** parses JSON + JUnit XML:
   - Builds mutation score: killed / total
   - Cross-references test method names (from JUnit XML) against killedByTest values
   - Flags tests never appearing in killedByTest as zombie candidates
   - Counts MockK/Mockito calls for over-mocking detection
   - Computes quality band + confidence level
4. **test-refactor-specialist** reviews flagged zombie candidates + over-mocked tests, generates improved test code

### Known limitation

The approximate zombie detection (killedByTest cross-reference) produces false positives — a test that doesn't exercise mutated code will never appear as killedByTest, but isn't necessarily a zombie. The auditor must annotate flagged tests with confidence levels (low if the test doesn't appear to exercise the mutated class). Full per-test-per-mutation precision (Scott-CC's model) requires either forking mutflow's MutFlowSession to track ALL failing tests per mutation, or running each test individually per mutation (exponential cost). This is deferred to v2.
