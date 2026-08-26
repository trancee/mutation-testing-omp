Type: grilling
Status: resolved
Blocked by: 03

## Answer

**D3 resolved — execution gap reporting approach.** All 4 decisions confirmed from R3's research:

| Decision | Choice | Rationale |
|---|---|---|
| **Gap types** | All 5 separate (COMPILATION_FAILURE, IR_TRANSFORMATION_ERROR, BACKSTOP_TIMEOUT, PARTIAL_RUN, NO_OUTPUT) | Separate types enable distinct error messaging and future handling, even if detection overlaps |
| **Detection layers** | Distributed — test-executor + MutationResultsTask/parser | Matches OMP's architecture (executor = capture exit code/missing XML/timeout; parser = analyze build result + XML); most robust for partial-run detection |
| **Score formula** | Null when (total - gaps) == 0 | Mutation score becomes nullable; mirrors Scott-CC's "never manufacture a score" principle; 0.0 implies all survived, misleading when zero evaluations |
| **TimedOut** | NOT a gap — valid result | Mutation was fully evaluated; excluding inflates score |

**Implementation changes required:**

- `MutationResults.kt`: Add `gaps: Int`, `mutationsEvaluated: Int` fields; make `mutationScore` nullable (`Double?`); add `ExecutionGap` data class and `execution_gaps: List<ExecutionGap>` field
- `MutationResultsParser.kt`: Add `detectGaps()` function; update `calculateMetrics()` to use new formula
- `.omp/agents/test-executor.md`: Document exit-code + missing XML + timeout checks
- `.omp/mutation-results.gradle.kts`: Check `test` task build result in `generateResults()`

### Frontier after D3 resolution:
- All 6 straightforward task features (T1: modes, T3: --focus, T4: auto-approve, T6: confidence intervals, T7: gap reporting implementation, T8: rollback) — ready for independent implementation
- The implementation phase begins (no more wayfinding tickets pending)

## Answer

**R3 resolved.** See `research/03-research-execution-gap-reporting.md` (346 lines). Ready for grilling.

## Question

How should execution gap reporting be adapted for OMP's mutflow-based model?

### Background

R3 will research what constitutes an execution gap in mutflow's in-process model (where syntax errors can't occur). This ticket decides the approach.

### Task

1. Review R3's findings on mutflow-side gap scenarios.
2. Decide:
   a. **Gap definition**: What constitutes an execution gap in OMP? (e.g., compilation failures, IR transformation errors, test class-level failures)
   b. **Detection**: Where in the pipeline are gaps detected? (test-executor, Gradle task, parser)
   c. **Reporting**: How are gaps excluded from the mutation score denominator?
   d. **Output**: Should gaps be in the JSON artifact, the audit report, or both?

### Acceptance criteria

- Chosen gap definition
- Chosen detection point in the pipeline
- Chosen exclusion mechanism from score
- Chosen output format
- Decision recorded as a resolution comment
