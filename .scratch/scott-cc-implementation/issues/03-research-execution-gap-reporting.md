Type: research
Status: resolved
Blocked by: (none)

## Answer

**R3 (ResearchExecutionGapReporting) complete.** Full findings written to `research/03-research-execution-gap-reporting.md` (346 lines).

### Key findings
1. **Scott-CC tracks 2 per-mutation gap types**: `ERROR` (env/infra failure — e.g. ModuleNotFoundError) and `INVALID_MUTATION` (saboteur introduced syntax error). Both excluded from score denominator via an `executable` filter. If `mutations_evaluated` is zero → `mutation_score: null` (never manufactured a score).
2. **mutflow has only 3 result types**: `Killed`, `Survived`, `TimedOut`. No ERROR/INVALID_MUTATION possible — mutations are IR-level compile-time injections compiled together; syntax errors fail at compile time for the whole class, not per-mutation.
3. **OMP CAN produce gaps at test-class granularity**: Compilation failure (IR transform error), IR transformation error, backstop timeout (15-min OMP limit), partial/truncated JUnit XML, test-class-level setup failures. These affect ALL mutations for a test class, not individual mutations.
4. **TimedOut is NOT a gap** — it's a valid fully-evaluated result (mutation caused infinite loop, mutflow detected it at 60s). Remains in score denominator.
5. **Proposed adapted approach**: Distributed detection — test-executor checks Gradle exit code + missing XML + timeout; MutationResultsTask/parser checks build result + XML presence. New score formula: `killed / (total - gaps)`, null when denominator is 0. New `execution_gaps` array in JSON with type/reason/test_class/affected_source_location.
## Question

How should execution gap reporting be adapted for OMP's mutflow-based model, where mutflow doesn't produce ERROR/INVALID_MUTATION results?

### Background

Scott-CC's test-executors can return ERROR (test suite failed to execute) or INVALID_MUTATION (syntax error from mutation). These are tracked as `execution_gaps` and excluded from the mutation score denominator.

OMP's mutflow doesn't have this concept — mutations are injected at compile time (IR level), so syntax errors shouldn't occur. But there are still scenarios where mutations might not be fully evaluated:
- Compilation failures from IR transformation errors
- Timeout-related gaps (though mutflow handles these as TimedOut)
- Test class failures (e.g., the entire test class fails, not individual mutations)

### Task

1. Examine Scott-CC's test-auditor and test-executor agents for how they handle ERROR/INVALID_MUTATION: what data they capture, how they report it, how it affects the score.
2. Examine OMP's test-executor agent (`.omp/agents/test-executor.md`) and the Gradle task (`.omp/mutation-results.gradle.kts`) and typed module (`MutationResultsParser.kt`) to identify what mutflow's output provides: `Killed`, `Survived`, `TimedOut` results.
3. Research what scenarios in mutflow's model could produce "gaps" — compilation failures, IR transformation errors, test class-level failures, partial results.
4. Propose an adapted execution gap reporting approach for OMP: what constitutes a gap, how to detect it, how to exclude it from the score denominator.

### Acceptance criteria

- Analysis of Scott-CC's gap reporting and how it maps to OMP
- Identification of mutflow-side scenarios that could produce gaps
- Proposed adapted gap reporting approach for OMP
- Findings captured in this issue's resolution
