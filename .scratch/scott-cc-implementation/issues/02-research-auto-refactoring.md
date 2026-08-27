Type: research
Status: resolved
Blocked by: (none)

## Question

What is the best approach for auto-generating production-ready refactored Kotlin test code in OMP's test-refactor-specialist agent?

### Background

Scott-CC's test-refactor-specialist generates full, production-ready refactored test files (consolidated parameterized tests, edge case additions, zombie removals). OMP's test-refactor-specialist currently produces suggestions only — no code generation.

This feature depends on redundant test detection (R1) — the refactor specialist needs to know which tests are redundant before it can consolidate them.

### Task

1. Examine Scott-CC's test-refactor-specialist agent for its code generation approach: what patterns it uses (parameterized tests, consolidation, edge case generation), how it produces the full file, how it computes metrics (old/new test count, estimated mutation score improvement).
2. Examine OMP's test-refactor-specialist agent (`.omp/agents/test-refactor-specialist.md`) to understand its current capabilities and constraints (tools available: read, edit, write, grep, glob, bash).
3. Research Kotlin test framework patterns for auto-generated tests: JUnit 5 `@ParameterizedTest` + `@MethodSource`/`@ValueSource`, Kotest property-based testing, Spek.
4. Determine whether the refactor specialist should produce full test file content (like Scott-CC) or incremental patches/edits.

### Acceptance criteria

- Findings captured in `research/02-research-auto-refactoring.md` and referenced from the wayfinder map's "Decisions so far" section.

## Answer

**Scott-CC vs OMP comparison:** Scott-CC's test-refactor-specialist (lines 1–493) generates full production-ready test files in a 5-step workflow: read existing file → analyze structure → generate refactored code → create complete file → produce git diff. Output is a JSON contract with `refactored_test_code`, `changes`, `metrics`, `diff`, `recommendations`, `warnings`. OMP's agent description (`.omp/agents/test-refactor-specialist.md`) already declares "Return the full refactored test file content" as its output format, but no code-generation mechanism is wired into the pipeline — the gap is implementation, not intent.

**Recommended Kotlin test framework:** JUnit 5 `@ParameterizedTest`. OMP's existing buildSrc tests use JUnit 5 Jupiter (`org.junit.jupiter.api.Test`), the bootstrap installs JUnit 5/6 into target projects, and `@ParameterizedTest` with `@CsvSource`/`@ValueSource`/`@MethodSource` provides direct mapping to Scott-CC's pytest `@parametrize` and Jest `describe.each`. Kotest (not installed, requires new dependencies) and Spek (unmaintained, no parameterization) are rejected.

**Recommended output format:** Full file content (not incremental patches). Rationale: (1) agent contract already specifies it; (2) Kotlin's type safety makes incremental patches fragile (must maintain imports, companion objects, `@MethodSource` factories); (3) LLM agents naturally generate full files; (4) git diff is trivially derived by writing to a temp file and running `git diff`.

**Integration approach:** The refactor specialist consumes (1) `testKillerMatrix` from the audit JSON to derive redundant groups (cluster tests by identical/superset killer signatures, threshold >5 — matching Scott-CC's auditor); (2) `zombie_test_candidates` cross-referenced with `surviving_mutations` to target improvements; (3) `over_mocked_tests` list for mock-to-integration swaps; (4) reads production + test source files for code generation context. Apply mechanism: agent outputs content + diff + manifest; `test-quality-reviewer` orchestrator gates application via `--auto-approve` (planned as T4). Zombie/redundant deletion always requires explicit approval (Scott-CC command contract principle). R1's redundant-group detection findings will determine whether groups come from the auditor output or are derived from `testKillerMatrix`. R3's execution gap research confirms `TimedOut` is NOT a gap (valid evaluated result); the refactor specialist should skip test classes affected by compilation/IR errors or backstop timeouts.
