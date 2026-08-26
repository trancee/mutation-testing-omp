# R3 Research: Execution Gap Reporting — Adapting Scott-CC's Model for OMP/mutflow

**Status:** Research findings — resolved
**Date:** 2026-08-26
**Author:** ResearchExecutionGapReporting (R3)
**Source files:**
- Scott-CC: `citadelgrad/scott-cc/plugins/mutation-testing/agents/test-auditor.md` (lines 73-81 score formula; lines 192-254 `execution_gaps` handling)
- Scott-CC: `citadelgrad/scott-cc/plugins/mutation-testing/agents/test-executor.md` (lines 200-219 ERROR/INVALID_MUTATION output)
- OMP: `.omp/agents/test-executor.md` (lines 1-46)
- OMP: `.omp/agents/test-auditor.md` (lines 9-61)
- OMP: `.omp/mutation-results.gradle.kts` (lines 1-112, full file)
- OMP: `.omp/mutation-results-src/main/kotlin/io/omp/mutation/MutationResultsParser.kt` (lines 29-163)
- OMP: `.omp/mutation-results-src/main/kotlin/io/omp/mutation/MutationResults.kt` (lines 14-61)
- OMP: `.omp/mutation-results-src/main/kotlin/io/omp/mutation/MutationResult.kt` (lines 14-30)
- OMP: `.omp/mutation-results-src/main/kotlin/io/omp/mutation/MutationResultsSerializer.kt` (lines 13-28)
- Comparison matrix: `.scratch/scott-cc-comparison/research/02-comparison-matrix.md` (rows 2.9, 7.1-7.7)
- OMP domain model: `.scratch/scott-cc-comparison/domain-model.md` (lines 38-53)
- R3 issue ticket: `.scratch/scott-cc-implementation/issues/03-research-execution-gap-reporting.md`

**See also:** `.scratch/scott-cc-implementation/issues/06-decide-execution-gap-reporting.md` (D3 decision ticket, blocked by this research)

---

## 1. Executive Summary

Scott-CC reports execution gaps for two per-mutation failure modes — `ERROR` (test suite could not execute) and `INVALID_MUTATION` (the mutation itself introduced a syntax error) — and excludes both from the mutation score denominator. These are possible because Scott-CC uses one isolated git worktree per mutation, so each worktree's test run can independently fail due to environment issues or bad mutations.

OMP/mutflow does not produce these failure modes. mutflow injects mutations at the Kotlin **IR level at compile time** (compile-once meta-mutant, see comparison matrix 1.1-6.5). All mutations are compiled together into a single binary; at runtime, mutflow's JUnit 6 extension selects one active mutation per run and runs the test suite. mutflow's `MutationResultType` enum has exactly three values: `Killed`, `Survived`, `TimedOut` — no `ERROR` or `INVALID_MUTATION`. Syntax errors from mutations cannot occur at runtime because they would fail at compile time first, failing the entire Gradle `test` task for the whole test class (not a single mutation). mutflow's `TestExecutionExceptionHandler` also swallows test failures during mutation runs, so JUnit always sees "passed" — there is no per-mutation "error" state.

However, OMP-side scenarios **can** produce gaps that affect mutation evaluation: **compilation failures**, **IR transformation errors**, **backstop timeouts** (15-minute OMP limit), and **partial/truncated runs**. These occur at the test-class granularity (not per-mutation), because mutflow's architecture compiles all mutations for a test class together. The adaptation: shift gap detection from per-mutation executor-agent status to the Gradle task / parser level, where compilation failures and truncated output can be detected, and represent the entire affected test-class's mutation set as gaps.

**Key finding on TimedOut:** A `TimedOut` result is **not** an execution gap. The mutation was fully evaluated — it caused an infinite loop and mutflow detected it. It should remain in the score denominator as a valid (non-killed) result. The wayfinder map's "Not yet specified" question (whether TimedOut counts as a gap) is resolved: it does not.

---

## 2. Scott-CC Gap Analysis

### 2.1 What Scott-CC tracks as execution gaps

Scott-CC's test-auditor (test-auditor.md, Output Format section) explicitly handles two gap-producing statuses from the test-executor agents:

**ERROR** — The test suite failed to execute at all. The executor returns:
```json
{
  "mutation_id": "mut-003",
  "error": "ModuleNotFoundError: No module named 'stripe'",
  "status": "ERROR",
  "recommendation": "Check dependencies in worktree"
}
```
This is an environment/infrastructure failure: missing dependencies, import errors, or other issues preventing the test suite from running.

**INVALID_MUTATION** — The mutation itself introduced a syntax error. The executor returns:
```json
{
  "mutation_id": "mut-005",
  "error": "SyntaxError: invalid syntax (stripe_handler.py, line 47)",
  "status": "INVALID_MUTATION",
  "recommendation": "Saboteur created invalid mutation - skip this one"
}
```
This means the LLM-guided saboteur produced a mutation that breaks the source code's syntax.

Both are captured in the auditor's output JSON in the `execution_gaps` array:
```json
"execution_gaps": [
  {"mutation_id": "...", "status": "...", "reason": "..."}
]
```

### 2.2 How gaps affect the score

The auditor's mutation score calculation (test-auditor.md, lines 75-79) explicitly excludes ERROR/INVALID_MUTATION results:

```python
executable = [result for result in results if result['status'] == 'COMPLETED']
mutations_caught = count(executable where test_results.failed > 0)
mutations_survived = count(executable where test_results.failed == 0)
mutation_score = mutations_caught / len(executable)
```

Key design decisions:
- Only `status == 'COMPLETED'` results are counted as `executable` — the score denominator is the count of executable mutations.
- `mutations_evaluated` = `len(executable)` — reported in the summary with a stated reduced sample size when gaps exist.
- If `mutations_evaluated` is zero (all results were ERROR/INVALID_MUTATION), `mutation_score` and `quality_rating` are set to `null` — the auditor **never manufactures a score**.
- Gap entries include `mutation_id`, `status`, and `reason`, providing traceability.

### 2.3 Why Scott-CC tracks these gaps

Scott-CC's isolation model is **one git worktree per mutation** (comparison matrix rows 6.1-6.5). Each test-executor agent runs `pytest`/`npm test` in its own isolated worktree. This per-mutation granularity means:
- Each worktree can independently fail due to environment issues (ERROR)
- Each mutation can independently introduce a syntax error (INVALID_MUTATION)
- The saboteur is LLM-guided, so it may create semantically invalid mutations

The gap tracking is necessary because each mutation is an independent evaluation. A gap means "we could not evaluate this mutation, so it must not count in the score." Without gap exclusion, a syntax error in one mutation would depress the score unfairly. This is also consistent with the "Error Handling" section at the end of test-auditor.md (lines 376-387): "If test results are missing: report which mutations lack results; calculate partial score with caveat."

---

## 3. mutflow's Model (OMP)

### 3.1 What mutflow produces

mutflow's `MutationResultType` enum (MutationResult.kt, lines 26-30) has exactly three values:
```kotlin
enum class MutationResultType {
    @SerialName("Killed") Killed,
    @SerialName("Survived") Survived,
    @SerialName("TimedOut") TimedOut,
}
```

The OMP parser (MutationResultsParser.kt, lines 48-53) maps mutflow's console status icons to these types:
- `✓` → `Killed` (at least one test failure caught the mutation)
- `✗` → `Survived` (all tests passed — mutation not caught)
- `⏱` → `TimedOut` (infinite-loop mutation detected by mutflow's 60s internal timeout)

The parser's `else -> continue` (line 52) skips any unrecognized status icons, meaning only these three types are recognized.

### 3.2 Why ERROR/INVALID_MUTATION cannot occur

**No syntax errors from mutations:** mutflow operates at the Kotlin IR level (comparison matrix 1.16-1.17, 7.1). Mutations are IR branch injections applied during compilation — the "compile-once meta-mutant" model (domain-model.md, lines 30, 84-87). All mutations are compiled together into one binary. If a mutation produced invalid IR, it would fail at compile time. The Gradle `test` task would fail entirely, producing no mutflow summary output at all. There is no per-mutation "invalid mutation" result because all mutations share a single compilation step.

**No environment isolation issues:** mutflow runs all mutations in-process via JUnit 6 extension (test-executor.md, line 17; SKILL.md, line 50-52). The test environment is set up once during the baseline run (run 0). There is no per-mutation environment setup that could fail independently. Dependencies are resolved at build time; if they're missing, the entire `test` task fails, not individual mutations.

**Test execution exception swallowing:** mutflow's JUnit 6 extension uses a `TestExecutionExceptionHandler` that catches and swallows test failures during mutation runs (test-executor.md, line 32). From JUnit's perspective, all tests "pass" during mutation runs. There is no "error" state per mutation — either a test catches the mutation (Killed) or it doesn't (Survived). The only failure mode is timeout, which mutflow handles and reports as `TimedOut`.

### 3.3 OMP's current score calculation

The OMP parser's `calculateMetrics` (MutationResultsParser.kt, lines 93-122) does NOT implement gap exclusion:
```kotlin
val total = mutations.size
val killed = mutations.count { it.result == MutationResultType.Killed }
val survived = mutations.count { it.result == MutationResultType.Survived }
val timedOut = mutations.count { it.result == MutationResultType.TimedOut }
val score = if (total > 0) killed.toDouble() / total else 0.0
```

Score = `killed / total` — all parsed mutation results are counted in the denominator. There is no concept of "executable" vs. "gap" mutations. The comparison matrix (row 2.9) confirms this: OMP has no execution gap reporting.

---

## 4. OMP-Side Gap Scenarios

While mutflow's three-result model prevents per-mutation gaps, several infrastructure-level failure modes in OMP's pipeline can produce gaps where mutations are not fully evaluated. These operate at the **test-class granularity** (all mutations for a test class share a single Gradle build), not per-mutation.

### 4.1 Compilation failure (most likely gap scenario)

mutflow injects mutations at compile time via IR transformation (comparison matrix 1.16). If the IR transformation produces code that fails to compile (e.g., type mismatch, nullability issue, incompatible branch types), the Gradle `compileTestKotlin` (or `compileKotlin`) task fails. The `test` task never starts, and consequently the `mutationResults` task — which `dependsOn(tasks.matching { it.name == "test" })` (mutation-results.gradle.kts, line 48) — never runs either. No JUnit XML is produced, no `MutationTestingSummary` is printed, and no JSON artifact is generated.

**Scope impact:** All mutations for the affected test class are gaps. We cannot distinguish which specific mutations caused the compilation failure because all mutations are compiled together into one binary. The entire set of mutations targeted at that source file is lost.

**Note:** The `test` task's `ignoreFailures = true` (mutation-results.gradle.kts, line 53) only applies to test execution failures (mutation kills), NOT to compilation failures. Compilation failures happen in the separate `compileTestKotlin` task, which `ignoreFailures` does not affect.

### 4.2 IR transformation error

If mutflow's IR transformer encounters an edge case it cannot handle, the compiler plugin itself may fail (throwing an exception during transformation). This would also cause compilation to fail, producing the same outcome as 4.1 — no mutflow output, no JSON artifact.

Unlike Scott-CC's LLM saboteur (which can produce syntactically invalid mutations 4.2 per the comparison matrix), mutflow's predefined operators are more robust, but IR-level edge cases (e.g., complex generic types, inline functions, suspending lambdas) can still trigger transformation errors.

### 4.3 Backstop timeout (partial run)

OMP's test-executor (test-executor.md, line 21) sets a 15-minute backstop timeout for the Gradle task. If this triggers:
- The test process is killed mid-run
- JUnit XML files may be partially written or missing (no closing `</testsuite>` tags)
- mutflow's `MutationTestingSummary` console output may be incomplete — some mutations never evaluated
- The `mutationResults` task may parse partial JUnit XML, finding some mutations but not all

**Scope impact:** This is a partial gap. Some mutations may have completed (appearing in the truncated output), while others never ran. The parser would find fewer mutations than expected but has no way to know which are missing — it only sees what was printed before the kill.

### 4.4 Truncated/partial JUnit XML

If the test process is killed (by timeout or OOM), JUnit XML files may be malformed:
- Missing closing tags → XML parsing fails entirely
- Partial `<system-out>` content → mutflow summary is truncated
- No `<system-out>` elements → parser finds no mutation results

The OMP parser (MutationResultsParser.kt, line 37) filters blank lines and parses line-by-line, so it can handle partially truncated output — it will parse whatever complete mutation result lines exist and skip the rest. But it has no mechanism to detect that lines are missing.

### 4.5 Test class-level setup/teardown failures

If a test class fails during initialization (e.g., `@BeforeEach`/`@TestInstance` setup throws), mutflow's JUnit 6 extension handles this at the class level. All mutation runs for that class could be affected. Since mutflow's multi-run model uses `ClassTemplateInvocationContextProvider` (comparison matrix 6.5), a class-level failure could prevent any mutation from being evaluated for that class.

However, mutflow's `TestExecutionExceptionHandler` catches failures during individual test methods, not class initialization. A class-level initialization failure would cause the entire test class to fail to run, producing no mutation results.

### 4.6 What IS NOT a gap

**`TimedOut` results:** The wayfinder map's "Not yet specified" section asks whether TimedOut should count as a gap. It should **not**. A TimedOut result means mutflow fully evaluated the mutation — the mutation was active, tests ran, and the 60s internal timeout triggered (indicating an infinite loop introduced by the mutation). This is a valid, evaluated result. It counts in the denominator as a non-killed mutation, same as Survived. Excluding TimedOut from the denominator would inflate the mutation score by discarding a real finding (infinite-loop mutations are bugs that tests failed to catch).

---

## 5. Comparison: Scott-CC vs. OMP Gap Models

| Aspect | Scott-CC | OMP/mutflow |
|---|---|---|
| **Gap granularity** | Per-mutation (one git worktree per mutation) | Per-test-class (all mutations compiled together into one binary) |
| **Gap type: syntax error** | `INVALID_MUTATION` — saboteur introduced syntax error | Cannot occur at runtime — IR errors fail at compile time, failing the whole class |
| **Gap type: environment failure** | `ERROR` — missing deps, import errors in worktree | Cannot occur per-mutation — env set up once at build time |
| **Gap type: timeout** | Not applicable (worktree isolation, no per-mutation timeout) | `TimedOut` — but this is a **valid result**, not a gap |
| **Gap type: backstop timeout** | N/A (parallel worktrees, no shared timeout) | 15-min OMP timeout → partial run, some mutations never evaluated |
| **Gap type: compilation failure** | N/A (Python/JS, no compilation) | Gradle `test` task fails → no mutflow output for the entire test class |
| **Gap type: IR transform error** | N/A (no compilation/IR) | Compiler plugin fails → no mutflow output for the entire test class |
| **Gap type: partial/truncated output** | N/A (structured JSON handoff from agent) | JUnit XML truncated → parser finds fewer mutations than expected |
| **Detection point** | test-executor agent returns `status` field in JSON | Gradle task exit code + JUnit XML parser + `mutationResults` task |
| **Score denominator** | `executable` = results where `status == 'COMPLETED'` | Currently: all parsed mutations (`total`); no exclusion mechanism |
| **Null-score handling** | If `mutations_evaluated` is zero → `mutation_score: null` | If parser finds zero mutations → score = 0.0 (not null) |
| **Data source** | Agent JSON with explicit `status` field per mutation | JUnit XML `<system-out>` + Gradle exit code + console output |
| **Traceability** | `{"mutation_id", "status", "reason"}` per gap | No equivalent — gaps are invisible in current output |

### Key architectural difference

Scott-CC's gap model is **intrinsic** to its isolation mechanism: each mutation gets its own worktree, so each mutation's test run can independently succeed or fail. The gap is a per-mutation evaluation outcome.

OMP's gap model would be **extrinsic** to mutflow: gaps arise from the Gradle build infrastructure (compilation, timeouts, truncation), not from mutflow's mutation evaluation. All mutations for a test class share the same build, so a build failure affects all of them simultaneously. There is no per-mutation gap status — only a binary "did the build produce mutflow output or not."

---

## 6. Proposed Adapted Approach for OMP

### 6.1 Gap definition for OMP

An execution gap in OMP = **a mutation that was injected by mutflow but could not be fully evaluated** due to infrastructure-level failures (not mutation-level test outcomes). Specifically:

| Gap type | Description | Source |
|---|---|---|
| `COMPILATION_FAILURE` | The Gradle `test` task's compilation step failed (IR transformation produced uncompilable code). All mutations for the test class are gaps. | Gradle exit code ≠ 0 before test execution |
| `IR_TRANSFORMATION_ERROR` | mutflow's compiler plugin failed during IR transformation. All mutations for the test class are gaps. | Gradle/compiler error output |
| `BACKSTOP_TIMEOUT` | The 15-minute OMP backstop killed the test run. Some mutations may not have been evaluated; the parser found fewer than all printed summaries. | Process killed, partial JUnit XML |
| `PARTIAL_RUN` | JUnit XML was truncated or incomplete (malformed XML, missing `<system-out>` content). The parser detected incomplete output. | Parser detects truncation |
| `NO_OUTPUT` | The Gradle `test` task produced no JUnit XML or no mutflow summary at all. All mutations for the test class are gaps. | Empty or missing XML files |

**Explicitly NOT gaps:** `Killed`, `Survived`, and `TimedOut` are all valid mutation evaluation results. A `TimedOut` mutation was fully evaluated (mutflow ran it, tests timed out at 60s, mutflow caught it) — it should remain in the score denominator.

### 6.2 Detection point in the pipeline

Gap detection should be **distributed** across two components, matching OMP's architecture where the Gradle task is the thin adapter and the parser is the pure logic:

**Layer 1 — test-executor agent** (the first detector):
The `test-executor` agent (`.omp/agents/test-executor.md`, line 21) runs `./gradlew test` and captures the exit code, stdout, and JUnit XML. It should detect:
- Gradle exit code ≠ 0 (before `test` ran → compilation/transformation failure)
- Missing JUnit XML files
- The 15-minute backstop triggering (process killed)
- Report these to the `test-auditor` as gap metadata alongside whatever partial output was captured

This is analogous to Scott-CC's test-executor returning a `status` field alongside test results.

**Layer 2 — `MutationResultsTask` / parser** (the second detector):
The `MutationResultsTask.generateResults()` function (mutation-results.gradle.kts, lines 67-111) should:
- Check the `test` task's build result (failed or succeeded)
- Check for presence of JUnit XML files
- Parse whatever output exists
- If the build failed or no XML was found, record gaps for the entire test class
- If partial output was found (some mutations parsed, but the build didn't complete cleanly), record which mutations were parsed and flag the rest as potential gaps

The `MutationResultsParser` should add a `detectGaps()` function that examines:
- Whether the stdout was empty
- Whether the parsed mutation count is consistent with expected (if available)
- Whether mutflow's summary footer (total count line) matches parsed count

This keeps pure logic in the testable module (comparison matrix 7.4: "Pure Kotlin functions in `MutationResultsParser` object — unit-tested independently") and the Gradle-integration concern in the task class.

### 6.3 Exclusion from the score denominator

The `MutationStats` calculation in `calculateMetrics()` (MutationResultsParser.kt, lines 93-122) should be updated:

1. Add a `gaps: Int` field to `MutationStats` (MutationResults.kt, lines 31-39)
2. Add an `execution_gaps: List<ExecutionGap>` field to `MutationResults` (MutationResults.kt, lines 48-61)
3. New score formula: `score = killed.toDouble() / (total - gaps)` when `total - gaps > 0`, else `null` (mirroring Scott-CC's "never manufacture a score" principle)
4. `mutations_evaluated = total - gaps` (reported in the JSON output)

This mirrors Scott-CC's approach: `executable = total - gaps`, `mutation_score = caught / executable`.

### 6.4 Output format

Add an `execution_gaps` array to the `mutation-results.json` artifact, analogous to Scott-CC's format but adapted for OMP's test-class-level granularity:

```json
{
  "generatedAt": 1724...,
  "mutationScore": 0.45,
  "qualityBand": "Fair",
  "confidence": "Medium",
  "totalMutations": 20,
  "mutations_evaluated": 17,
  "killed": 9,
  "survived": 6,
  "timedOut": 2,
  "gaps": 3,
  "execution_gaps": [
    {
      "type": "COMPILATION_FAILURE",
      "reason": "IR transformation error: type mismatch in Calculator.multiply()",
      "test_class": "CalculatorTest",
      "affected_source_location": "(Calculator.kt:45)",
      "gradle_exit_code": 1
    },
    {
      "type": "BACKSTOP_TIMEOUT",
      "reason": "15-minute OMP backstop timeout triggered",
      "test_class": "PaymentServiceTest",
      "affected_mutations": ["(PaymentService.kt:12)", "(PaymentService.kt:18)"]
    }
  ],
  "mutations": [...],
  "testMethods": [...],
  "testKillerMatrix": {...}
}
```

**Design decisions for the output format:**
- Each gap entry has a `type` (enum: `COMPILATION_FAILURE`, `IR_TRANSFORMATION_ERROR`, `BACKSTOP_TIMEOUT`, `PARTIAL_RUN`, `NO_OUTPUT`), a `reason` (human-readable explanation from Gradle/compiler output), and `test_class` (which test class was affected, since gaps are class-level in OMP).
- `mutations_evaluated` is explicitly reported (total minus gaps), mirroring Scott-CC's `mutations_evaluated` field, so consumers can see the reduced sample size.
- The `mutations` array only contains successfully parsed results — gaps are not mutation entries, they're a separate list.

### 6.5 Null-score handling

Following Scott-CC's principle ("If `mutations_evaluated` is zero, set `mutation_score` and `quality_rating` to `null`; never manufacture a score"):

- If `total - gaps == 0` (all mutations are gaps), set `mutationScore` to `null` and `qualityBand` to `null`.
- This prevents a misleading 0.0% score that would imply the test suite caught nothing, when in reality the tests never ran.

### 6.6 Backward compatibility

The new `execution_gaps` and `gaps` fields should use `encodeDefaults = true` (already configured in MutationResultsSerializer.kt, line 17) so they appear in the JSON output even when empty. The `ExecutionGap` data class should be `@Serializable` and added alongside the existing types in the mutation-results-src module. The `MutationResultsParserTest` suite (18 existing tests) should gain new test cases for gap detection scenarios.

---

## 7. Summary of Findings

| Question | Answer |
|---|---|
| What does Scott-CC track as execution gaps? | `ERROR` (env/infra failure preventing test execution) and `INVALID_MUTATION` (saboteur introduced syntax error), both per-mutation via isolated git worktrees. |
| What does mutflow's model produce? | Exactly `Killed`, `Survived`, `TimedOut` — no ERROR/INVALID_MUTATION. IR-level compile-time injection prevents syntax errors; in-process execution prevents env failures; exception swallowing prevents per-mutation errors. |
| What OMP-side scenarios produce gaps? | Compilation failures (IR transform errors fail at build time), IR transformation errors (compiler plugin failures), backstop timeouts (15-min OMP limit kills partial runs), and truncated/partial JUnit XML output. All at test-class granularity, not per-mutation. |
| Is TimedOut a gap? | No. TimedOut is a valid, fully-evaluated result. Excluding it would inflate the score. |
| Gap definition for OMP? | A mutation that was compiled by mutflow but could not be fully evaluated due to infrastructure failures (build failure, timeout, truncation), at the test-class level. |
| Detection point? | Distributed: test-executor agent (exit code + missing XML) → `MutationResultsTask` (build result check) → `MutationResultsParser.detectGaps()` (output analysis). |
| Exclusion from score? | `score = killed / (total - gaps)`, with null score when `total - gaps == 0`. Mirrors Scott-CC's `executable` filter. |
| Output format? | `execution_gaps` array in `mutation-results.json` with type/reason/test_class, plus `mutations_evaluated` and `gaps` count fields. Backward-compatible via `encodeDefaults = true`. |

### Open questions for D3 decision ticket

1. **Gap granularity**: Since gaps are test-class-level (not per-mutation), should the gap entry list individual affected mutations when partial output is available, or just the test class? Recommendation: when partial output exists, list the missing mutation source locations; when no output exists, report the test class and a representative reason.

2. **Expected mutation count**: To detect "partial runs" (some mutations missing from truncated output), the parser needs to know how many mutations were expected. mutflow's summary footer prints a total count. The parser should capture this and compare against parsed count. If they differ, the remainder are gaps.

3. **Integration with the `mutationResults` task lifecycle**: The task currently sets `testTask.ignoreFailures = true` via `whenReady`. For gap detection, the task also needs access to the test task's build result (success/failure). This may require registering a `project.gradle.buildFinished` handler or checking `testTask.state` in `generateResults()`.
