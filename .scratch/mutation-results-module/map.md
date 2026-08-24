## Destination

Collapse the 224-line `.omp/mutation-results.gradle.kts` Gradle script into a typed Kotlin module with a `MutationResults` data model backed by kotlinx.serialization. The `MutationResultsTask` Gradle class becomes a thin adapter that delegates parsing to a pure, unit-tested Kotlin module. JSON output must remain byte-for-byte backward-compatible with the existing `mutation-results.json` schema consumed by test-auditor.

## Notes

- **Domain docs**: `CONTEXT.md` (mutation testing, zombie tests, over-mocked tests), ADR-001 (mutflow as engine), ADR-002 (5-agent orchestration)
- **Issue tracker**: local markdown — `.scratch/mutation-testing-omp/` is a separate effort; this map lives at `.scratch/mutation-results-module/`
- **Skills to consult**: grilling (for design decisions), domain-modeling (for data model), research (for Gradle mechanics)
- **Key constraint**: the typed module must produce `mutation-results.json` with the exact same fields, keys, and structure as the current string-template output — `generatedAt`, `mutationScore`, `qualityBand`, `confidence`, `totalMutations`, `killed`, `survived`, `timedOut`, `testMethods`, `testKillerMatrix`, `mutations[].sourceLocation|originalOperator|variantOperator|result|killedByTest|killedByTests`
- **Current parsing logic** lives in `MutationResultsTask.parseMutflowSummary()` (lines 168–222 of `.omp/mutation-results.gradle.kts`) — returns `List<Map<String, Any?>>`, no tests
- **Frontend**: `.omp/` is applied to target projects via `apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))` injected by `bootstrap-mutation-testing.sh`

## Decisions so far

- [research-gradle-kotlin-init-script](issues/01-research-gradle-kotlin-init-script.md): **buildSrc with kotlin-dsl + kotlinx-serialization is the mechanism** — Gradle Kotlin DSL scripts cannot use the serialization compiler plugin; the typed module must live in `buildSrc/src/main/kotlin/` of the target project, compiled with the `kotlin-dsl` plugin. Findings: [research/01-findings.md](research/01-findings.md)
- [grilling-data-model](issues/02-grilling-data-model.md): **flat @Serializable data classes** (not sealed) to match the exact JSON shape. `MutationResult` has all 6 fields on every variant; `MutationResults` aggregates with quality bands and testKillerMatrix. Pure functions for parsing/metrics/JSON. Thresholds: Excellent >80%, Good >60%, Fair >30%, Poor else; Low <10, Medium ≤50, High >50.

## Not yet specified

(none — all four questions are ticketed as 02–05)

## Status: complete ✅

All 5 tickets resolved and merged. See commits `cd77e56`, `b658bf7`, `8551502`, `ab16173` on `deepen-mutation-results-module` branch (PR #1, merged to main).

### Resolution summary
- **Typed module** in `.omp/mutation-results-src/` with `@Serializable` data classes, pure parser functions, kotlinx.serialization JSON
- **buildSrc generation** in `bootstrap-mutation-testing.sh` — Step 3b generates typed module with Kotlin version detected from target project; also fixed `--jvm` default flag (was broken when called without explicit mode)
- **18 unit tests** pass — parser, stats, serialization
- **Backward compatible** — JSON output matches original schema byte-for-byte
- **112-line adapter** (down from 224 lines) — `.omp/mutation-results.gradle.kts` delegates to buildSrc types

