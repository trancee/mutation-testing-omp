# Implement 9 Scott-CC→OMP feature gaps in OMP's mutation-testing system

Labels: wayfinder:map

## Destination

**Implement the 9 Scott-CC→OMP feature gaps in OMP's mutation-testing system, shipping each feature independently (incremental delivery). Implementation approaches must be decided for the 3 foggy features (redundant test detection, auto-generated refactoring, execution gap reporting); the remaining 6 are straightforward tasks.**

Reaches from here when: each of the 9 features has a clear implementation approach and any necessary decisions resolved, the dependency graph is wired, and the frontier (open, unblocked, unclaimed tickets) represents the next implementable units.

## Notes

**Source of truth:** [comparison matrix](../scott-cc-comparison/research/02-comparison-matrix.md) — R2's 51-row feature matrix. Domain model: [../scott-cc-comparison/domain-model.md](../scott-cc-comparison/domain-model.md). Original map: [../scott-cc-comparison/map.md](../scott-cc-comparison/map.md).

**Implementation architecture (domain model):**

| Component | File(s) | Features to implement here |
|---|---|---|
| `test-auditor` agent | `.omp/agents/test-auditor.md` | Redundant test group detection, execution gap reporting |
| `MutationResultsParser` | `.omp/mutation-results-src/main/kotlin/io/omp/mutation/MutationResultsParser.kt` | Confidence intervals, execution gap reporting |
| `test-refactor-specialist` agent | `.omp/agents/test-refactor-specialist.md` | Auto-generated refactored test code, diff generation, rollback instructions |
| `test-quality-reviewer` agent | `.omp/agents/test-quality-reviewer.md` | Quick/standard/deep modes, `--focus`, `--auto-approve` |
| `/mutation-test` skill | `.omp/skills/mutation-test/SKILL.md` | CLI flags: `--quick`/`--deep`, `--focus`, `--auto-approve` |

**Dependency graph** (decisions depend on research):
```
[R1: Redundant test detection] → D1 (decide detection approach, issue 04)
[R2: Auto-refactoring] → D2 (decide refactoring approach, issue 05)
[R3: Execution gap reporting] → D3 (decide gap reporting approach, issue 06)
```

**Issue tracker:** local markdown — `.scratch/scott-cc-implementation/`

**Straightforward tasks (no wayfinding needed — implement after map complete):**
- T1: Quick/standard/deep mode abstraction (M) — map flags to mutflow `maxRuns`
- T3: `--focus` parameter (S) — bridge CLI to Gradle `includeTargets`/`excludeTargets`
- T4: Auto-approve (M) — wire flag to test-refactor-specialist
- T6: Confidence intervals (S) — add statistical CI formula to MutationResultsParser
- T7: Execution gap reporting (S) — adapt gap concept for mutflow (see R3)
- T8: Rollback instructions (trivial) — add to final report

## Decisions so far

- [Research R1: redundant test detection](issues/01-research-redundant-test-detection.md): Scott-CC failure-signature algorithm reconstructable from OMP's `testKillerMatrix` + `mutations.killedByTests`. **Option C recommended** — detect in Kotlin module (`MutationResultsParser.detectRedundantTestGroups()`) + describe patterns in test-auditor agent. Threshold >5 (same as Scott-CC). Data model sufficient, no new data capture needed.
- [Research R2: auto-generated refactoring](issues/02-research-auto-refactoring.md): **Full test file content** (not patches) via **JUnit 5 `@ParameterizedTest`** (already a dependency). Apply gated by `--auto-approve`; zombie/redundant deletion always requires explicit approval. Consumes `test_killer_matrix`, `zombie_test_candidates`, `over_mocked_tests` from JSON.
- [Research R3: execution gap reporting](issues/03-research-execution-gap-reporting.md): **5 gap types** at test-class granularity: COMPILATION_FAILURE, IR_TRANSFORMATION_ERROR, BACKSTOP_TIMEOUT, PARTIAL_RUN, NO_OUTPUT. Detection distributed across test-executor (exit code + missing XML) and MutationResultsTask/parser. **TimedOut is NOT a gap** (valid result). New score: `killed/(total-gaps)`, null when denominator is 0.
- [Decided D1: redundant test detection approach](issues/04-decide-redundant-test-detection.md): Option C (both Kotlin module + test-auditor agent). Threshold >5 (same as Scott-CC). Hybrid data source (testKillerMatrix for grouping + mutations.killedByTests for precise output). Output in both JSON + report. User confirmed.
- [Decided D2: auto-refactoring approach](issues/05-decide-auto-refactoring.md): Full test file content via JUnit 5/6 `@ParameterizedTest`. Apply gated by `--auto-approve`; zombie/redundant deletion requires explicit approval. Refactor specialist reads pre-computed `redundantGroups` from JSON (leveraging D1's Option C). User confirmed.
- [Decided D3: execution gap reporting approach](issues/06-decide-execution-gap-reporting.md): 5 gap types (COMPILATION_FAILURE, IR_TRANSFORMATION_ERROR, BACKSTOP_TIMEOUT, PARTIAL_RUN, NO_OUTPUT) at test-class granularity. Distributed detection (test-executor + parser). Score: `null` when `(total - gaps) == 0`; TimedOut is a valid result, not a gap. Add `execution_gaps` array + `mutationsEvaluated` to JSON; make `mutationScore` nullable. User confirmed.

## Not yet specified

(none — all design decisions resolved)

## Out of scope

- All 9 features are in scope; this is purely the planning map before implementation begins.

## Status: All decisions resolved (D1, D2, D3). Frontier: 7 straightforward implementation tasks ready for independent shipping.
