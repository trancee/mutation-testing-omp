# ADR-001: Use mutflow as the mutation testing engine

- **Date**: 2026-08-22
- **Status**: Accepted
- **Decision Maker**: Grilling session (D1-D4 wayfinder tickets)

## Context

We need to choose a mutation testing engine for Kotlin (JVM-first) projects that:

1. Integrates with Kotlin/JVM via a compiler plugin
2. Supports JUnit 6
3. Can inject mutations at compile time
4. Provides a mutation score calculation

Three candidates were evaluated:

- **mutflow** (https://github.com/anschnapp/mutflow): Kotlin K2 compiler plugin using "meta-mutant" technique — injects all mutation variants at compile time with runtime selection. JUnit 6 native via `@MutFlowTest`. Requires Kotlin 2.4.x.
- **mutant-kraken** (Rust CLI): Standalone CLI with 5 stages. Operators include Arithmetic, Unary, Logical, Relational, etc. Beta quality (30 GitHub stars).
- **pitest/Arcmutate**: Java-first mutation testing via Maven/Gradle. Arcmutate extends with Kotlin/Spring/Git support. Fast for Java but KMP pain.

## Decision

Use **mutflow** as the mutation engine.

## Rationale

- **Kotlin-first**: mutflow is a native Kotlin compiler plugin — no Java interoperability layer needed
- **JUnit 6 native**: `@MutFlowTest` + `MutFlow.underTest { }` API is idiomatic Kotlin
- **Compile-once meta-mutant**: All mutations compiled into one build, runtime selects one per run. This eliminates the need for per-mutation git worktrees (Scott-CC's approach), simplifying the orchestration layer
- **Operator coverage**: mutflow's catalog covers 5 of 5 Scott-CC mutation strategies (boundary, return values, boolean logic, arithmetic, exception types). Exception type support is included in upstream mutflow v1.1.1+ via `ExceptionTypeSwapOperator`
- **Active maintenance**: mutflow is actively developed with Kotlin 2.4.x support

## Consequences

### Positive

- No git worktree management — mutflow handles isolation via compile-once
- Simpler orchestration: one executor per test class, not per mutation
- Fast iteration: single compilation covers all mutations
- Full per-test-per-mutation zombie detection: mutflow tracks all tests that kill each mutation (`MutationResult.Killed(testNames: Set<String>)`), enabling precise zombie candidate identification via `testKillerMatrix`

### Negative

- Non-JVM targets: Kotlin Multiplatform projects can mutate JVM source sets only. Kotlin/JS and Kotlin/Native are unsupported. Supporting them requires changes to the Gradle and compiler plugins.
- No LLM-guided mutations: all operators are predefined and static. LLM serves as targeting specialist (suppression annotations), not as a mutation generator
- Per-JVM synchronized lock: `MutationRegistry.withSession()` permits one active mutation session inside each JVM.

## Alternatives considered

- **mutant-kraken**: Rejected — Rust CLI with less Kotlin-specific support, beta quality (30 stars vs mutflow's active development)
- **pitest/Arcmutate**: Rejected — Java-first engine; KMP support via Arcmutate is an add-on, adds complexity
- **Building a custom engine**: Rejected — significant engineering effort, reinvention of proven approaches

## Tags

- engine
- kotlin
- jvm
