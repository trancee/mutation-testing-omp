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

### Decisions
1. **Full per-test-per-mutation zombie detection**: The mutflow fork's `MutFlowSession.markTestFailed()` now tracks ALL tests that catch each mutation (via `killedByTests: MutableSet<String>`). The `mutation-results.gradle.kts` task builds a `testKillerMatrix` mapping each test display name → list of mutation source locations it killed. Zombie candidates = tests in `testMethods` that have NO entry in `testKillerMatrix`.
2. **Quality bands with mutation-count confidence**: Mutation score = killed / total. Excellent >80%, Good 60-80%, Fair 30-60%, Poor <30%. Confidence: <10 mutations = low, 10-50 = medium, 50+ = high.
3. **Over-mocking detection via mock counting**: Parse test source files, count `mockk()`, `mock()`, `spyk()`, `@MockK`, `@Mock` per test method. Flag tests with >3 mocks as over-mocking candidates. LLM (test-refactor-specialist) reviews flagged tests.

### Answering the 5 ticket questions

| Q# | Answer |
|---|---|
| 1 | mutflow fork now tracks ALL tests that kill each mutation (via `killedByTests: MutableSet<String>` in `markTestFailed`). JUnit XML shows all tests as "passed" during mutation runs (failures swallowed by the extension). The custom Gradle task captures `killedByTests` array + `testKillerMatrix` for full per-test-per-mutation analysis. |
| 2 | Run model is test-class-level, not per-mutation. All tests in a class execute per mutation run (baseline + N mutation runs). All failing tests per run are tracked (not just the first). "Evaluated" = all tests in the class for each run. "Unevaluated" = tests that never fail and never appear in `testKillerMatrix`. |
| 3 | mutflow's partial run detection (executedTestIds check) skips mutation testing when running single tests from IDE. Via OMP, the test-executor runs the full class, so partial detection does NOT trigger. |
| 4 | Kotlin equivalent: MockK's `mockk()` and `spyk()`, Mockito-Kotlin's `mock()` and `mockOrNull()`. Annotations: `@MockK`, `@Mock`. Count per test method, flag >3 as candidates. |
| 5 | Custom Gradle task (D3 decision) outputs structured JSON instead of parsing console text. The JSON contains `killedByTests` array, `testKillerMatrix`, plus `pointId`, `variantIndex`, `result`. |

### Zombie detection data flow

1. **test-executor** runs `./gradlew test` (mutflow JUnit extension handles multi-run internally)
2. Custom Gradle task `mutationResults` parses JUnit XML `<system-out>` (mutflow's `MutationTestingSummary` with all `killed by:` lines) and outputs JSON: `{class, mutations: [{sourceLocation, result, killedByTests[], killedByTest}], testMethods[], testKillerMatrix: {test → [sourceLocations]}}`
3. **test-auditor** parses JSON + JUnit XML:
   - Builds mutation score: killed / total
   - Uses `testKillerMatrix` to identify zombie candidates (tests never killed any mutation)
   - Counts MockK/Mockito calls for over-mocking detection
   - Computes quality band + confidence level
4. **test-refactor-specialist** reviews flagged zombie candidates + over-mocked tests, generates improved test code

### Limitations

- Display name normalization: JUnit XML `name` attributes give method names (e.g., `testValidateInput`), while JUnit 5 `context.displayName` may add `()` suffix (e.g., `testValidateInput()`). The auditor normalizes by stripping trailing `()`.
- Surviving mutations still require manual investigation to determine if the mutation is genuinely untested or if the test is over-mocked.
