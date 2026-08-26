Type: research
Status: resolved
Blocked by: (none)

## Answer

**R1 (ResearchRedundantTestDetection) complete.** Findings in `research/01-redundant-test-detection.md`.

### Key findings
- **Algorithm**: Scott-CC builds failure signatures (test → mutation IDs it failed for), groups by identical signature, flags groups with >5 tests as redundant. Empty signatures (zombies) excluded from redundancy.
- **OMP data sufficiency**: SUFFICIENT. `testKillerMatrix` (test → mutation source locations) is functionally identical to Scott-CC's failure signature. `mutations` list with `killedByTests` provides full per-mutation granularity with composite key (sourceLocation, originalOperator, variantOperator).
- **Recommended integration**: **Option C — both Kotlin module and test-auditor agent.** Kotlin module: add `detectRedundantTestGroups()` to `MutationResultsParser`, `RedundantGroup` data class, `redundantGroups` field on `MutationResults`. Test-auditor: document algorithm, read pre-computed JSON, generate semantic pattern descriptions.
## Question

How should redundant test group detection be implemented in OMP's mutation-testing system?

### Background

Scott-CC's test-auditor identifies redundant tests by grouping tests that always fail together (same failure signature across mutations). If >5 tests have the same signature, they're flagged as a redundant group for consolidation.

OMP's test-auditor currently identifies zombie candidates (tests that never killed any mutation) using the `testKillerMatrix` from the typed JSON module. But it does NOT detect redundant groups.

### Task

1. Examine Scott-CC's test-auditor agent (`test-auditor.md`) for the redundant test group algorithm: how it builds failure signatures, how it groups tests, what threshold it uses (>5).
2. Examine OMP's test-auditor agent (`.omp/agents/test-auditor.md`) and the typed JSON module (`MutationResultsParser.kt`, `MutationResults.kt`) to identify what data is available for failure-signature construction (test outcomes per mutation, `killedByTests` arrays, `testKillerMatrix`).
3. Determine whether OMP's data model (per-test-per-mutation via `testKillerMatrix`) is sufficient to reconstruct failure signatures, or if additional data capture is needed.
4. Propose the integration point: should redundant test detection run in the test-auditor agent (in-memory), in the Gradle task (JSON output), or both?

### Acceptance criteria

- Description of the redundant test group algorithm
- Assessment of OMP data model sufficiency for failure signatures
- Recommended integration point(s) with rationale
- Findings captured in this issue's resolution
