---
name: mutation-test
description: Run mutflow-powered mutation testing on a Kotlin project via the 5-agent OMP system (test-quality-reviewer orchestrator, test-saboteur, test-executor, test-auditor, test-refactor-specialist). JVM-first.
---

## Mutation Testing

Runs a mutation-testing analysis on a Kotlin (JVM-first) project using mutflow as the engine and OMP's 5-agent system for orchestration.

### Usage

```
/mutation-test [project path] [--targets <test-class-pattern>]
/mutation-test setup [project path] [--kmp]
```

- `project path`: Path to the Kotlin project root (default: current directory)
- `--targets`: Optional glob pattern for test classes to include (default: all `@MutFlowTest` classes)
- `--kmp`: (setup only) Use Kotlin Multiplatform project setup (mutflow targets JVM source sets only)

### Setup subcommand

`/mutation-test setup [project path] [--kmp]` bootstraps the entire system into a new project:

1. **`.omp/` files copied**: agents, skills, `mutation-results.gradle.kts`, `mutation-results-src/` copied to `project-path/.omp/`
2. **`settings.gradle.kts`**: `pluginManagement` block added with `mavenCentral()` + `gradlePluginPortal()`
3. **`build.gradle.kts`**: mutflow plugin, JUnit 6 dependencies, `apply(from = ...)` for mutation-results added
4. **`buildSrc/` generated**: typed `MutationResults` module copied from `.omp/mutation-results-src/` with `kotlin-dsl` + `kotlinx-serialization` plugins
5. **`test-saboteur`** (via `task`) annotates business-logic classes with `@MutationTarget`, test classes with `@MutFlowTest`, and wraps existing assertions in `MutFlow.underTest { }`

### What happens (full mutation test run)

1. **`test-quality-reviewer`** (orchestrator) receives the task and coordinates the pipeline
2. **`test-saboteur`** analyzes source code, adds `@MutationTarget` to business-logic classes, `@MutFlowTest` to test classes, and suppression comments to framework noise
3. **`test-executor`** agents run `./gradlew test` for each test class — mutflow's JUnit 6 extension handles the multi-run model (baseline + mutation runs) internally
4. **`test-auditor`** parses JSON results + JUnit XML, calculates mutation score, identifies zombie test candidates, detects over-mocked tests
5. **`test-refactor-specialist`** generates improved test code for flagged issues

### Prerequisites

- Kotlin JVM project with Gradle
- Java 21+, Gradle 9.x+, Kotlin 2.4.x
- For fresh projects, use `/mutation-test setup` first

### mutflow architecture notes

Key mutflow constraints that affect orchestration:

- JVM-only — no JS/Native/Android support in v1
- Compile-once meta-mutant — all mutations injected at compile time, one active per run
- Global synchronized lock — serializes mutation runs; parallel executors block-and-wait
- Full per-test-per-mutation zombie detection — fork tracks all tests that kill each mutation (PR [#17](https://github.com/anschnapp/mutflow/pull/17) pending upstream)

For the full explanation of how mutflow's compile-once meta-mutant architecture works and why it matters for the OMP agent system, see [About mutflow's architecture](../../../docs/explanation/mutflow-architecture.md). For the 5-agent system and how each agent contributes, see [About the OMP 5-agent system](../../../docs/explanation/agent-system.md).

### Issue tracking

Decisions and issues tracked in `.scratch/mutation-testing-omp/`. See `docs/agents/issue-tracker.md` for the tracking workflow.

### Dispatch

This skill spawns subagents via the `task` tool:

- **Setup**: `task with agent: "test-quality-reviewer", task: "Bootstrap mutation testing system into [project path] with kmp=[--kmp]"` — the orchestrator runs the bootstrap script, then invokes test-saboteur to annotate existing tests.
- **Mutation test**: `task with agent: "test-quality-reviewer", task: "Run mutation testing on [project path] with targets [targets]"`
