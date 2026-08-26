Type: grilling
Status: resolved
Blocked by: 01

## Answer

**D1 resolved — redundant test detection approach.** All 3 research tickets (R1, R2, R3) are now complete. User confirmed:

| Decision | Choice | Rationale |
|---|---|---|
| **Integration point** | Option C — both Kotlin module + test-auditor agent | Reliable computation in module + semantic descriptions in agent (R1 recommendation, user confirmed) |
| **Threshold** | >5 tests per group | Same as Scott-CC; avoids over-flagging in smaller Kotlin test suites (user confirmed) |
| **Data source** | Hybrid — testKillerMatrix for grouping + mutations.killedByTests for precise output | testKillerMatrix (already computed) drives signature grouping; mutations list with composite keys provides precise failure_signature |
| **Output** | Both JSON artifact + audit report | `redundantGroups` array in `mutation-results.json` for programmatic access; test-auditor agent adds semantic pattern descriptions |
| **Blocked By** | 01 (R1 research) — resolved ✅ | |

### Implementation next steps (T1 — straightforward task, no wayfinding needed):
- Add `RedundantGroup` data class to `MutationResults.kt`
- Add `redundantGroups` field to `MutationResults`
- Add `detectRedundantTestGroups()` to `MutationResultsParser.kt` (unit test: exact signature matching, >5 threshold, zombie exclusion)
- Update test-auditor agent to describe patterns from pre-computed `redundantGroups` JSON

### Frontier after D1 resolution:
- D2 (05-decide-auto-refactoring) — blocked by 01, 02 (both resolved) → **unblocked**
- D3 (06-decide-execution-gap-reporting) — blocked by 03 (resolved) → **unblocked**
## Answer

**R1 resolved.** See `research/01-redundant-test-detection.md`. Ready for grilling.
## Question

How should redundant test group detection be integrated into OMP's mutation-testing system?

### Background

R1 will research the failure-signature algorithm and OMP's data model sufficiency. This ticket decides the implementation approach based on R1's findings.

### Task

1. Review R1's findings on the redundant test group algorithm.
2. Decide:
   a. **Integration point**: Should detection run in the test-auditor agent (in-memory), the Gradle task (JSON output), or both?
   b. **Data source**: Should it use the `testKillerMatrix` from the typed JSON module, or extend the mutation results model with failure signatures?
   c. **Threshold**: Should the threshold be >5 tests per group (same as Scott-CC), or adjusted for Kotlin projects?
   d. **Output**: Should redundant groups be included in the final audit report, the JSON artifact, or both?

### Acceptance criteria

- Chosen integration point with rationale
- Chosen data source with any required model extensions
- Chosen threshold
- Chosen output format
- Decision recorded as a resolution comment
