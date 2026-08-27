Type: grilling
Status: resolved
Blocked by: 01, 02

## Answer

**D2 resolved — auto-refactoring implementation approach.** User confirmed all 4 decisions from R2's research:

| Decision | Choice | Rationale |
|---|---|---|
| **Output format** | Full test file content | Kotlin type safety makes patches fragile; LLM agents generate full files naturally; diff trivially derived; matches Scott-CC and existing agent contract |
| **Test framework** | JUnit 5/6 `@ParameterizedTest` | Already a dependency; works identically with JUnit 5 and 6; direct mapping to Scott-CC's pytest `@parametrize`; Kotest/Spek rejected |
| **Apply mechanism** | Full file + diff + manifest, gated by `--auto-approve`; zombie/redundant deletion always requires explicit approval | Matches Scott-CC command contract: non-destructive changes auto-applied; deletions require explicit approval |
| **Data source** | Read pre-computed `redundantGroups` from JSON | Leverages D1's Option C output (Kotlin module already computes groups in `MutationResultsParser`); no duplicated logic; refactor specialist consumes JSON array |

### Implementation next steps (T2 — straightforward task, no wayfinding needed):
- Update `test-refactor-specialist.md` agent contract: consume `redundantGroups` from JSON, generate full JUnit 5 parameterized test files, produce diff + manifest
- Update `test-quality-reviewer.md` orchestrator: read manifest, gate application on `--auto-approve`, require explicit approval for deletions
- Update `/mutation-test` skill SKILL.md: add `--auto-approve` flag documentation and wiring

### Frontier after D2 resolution:
- D3 (06-decide-execution-gap-reporting) — blocked by 03 (resolved) → **unblocked and first in order**


## Question

What is the implementation approach for auto-generated refactored Kotlin test code in OMP's test-refactor-specialist agent?

### Background

R1 will research redundant test detection (needed as input for refactoring). R2 will research code generation approaches for Kotlin tests. This ticket decides the overall approach.

### Task

1. Review R1 and R2 findings.
2. Decide:
   a. **Output format**: Full refactored test file content (like Scott-CC) or incremental patches/edits?
   b. **Test framework**: JUnit 5 `@ParameterizedTest` (most likely), Kotest, or Spek?
   c. **Apply mechanism**: How does the generated code get applied? Write to file? Git diff? User approval gate?
   d. **Dependency on detection**: How does the refactor specialist consume redundant test group detection results from the auditor?

### Acceptance criteria

- Chosen output format with rationale
- Chosen test framework with rationale
- Chosen apply mechanism
- Integration approach with the audit pipeline
- Decision recorded as a resolution comment
