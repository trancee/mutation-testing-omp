---
name: mutation-test
description: Run mutflow-powered mutation testing on a Kotlin project via the 5-agent OMP system (test-quality-reviewer orchestrator, test-saboteur, test-executor, test-auditor, test-refactor-specialist). JVM-first.
---

## Mutation Testing

Runs a mutation-testing analysis on a Kotlin (JVM-first) project using mutflow as the engine and OMP's 5-agent system for orchestration.

### Usage

```
/mutation-test [project path] [--targets <test-class-pattern>]
```

- `project path`: Path to the Kotlin project root (default: current directory)
- `--targets`: Optional glob pattern for test classes to include (default: all `@MutFlowTest` classes)

### What happens

1. **`test-quality-reviewer`** (orchestrator) receives the task and coordinates the pipeline
2. **`test-saboteur`** analyzes source code, adds `@MutationTarget` to business-logic classes, `@MutFlowTest` to test classes, and suppression comments to framework noise
3. **`test-executor`** agents run `./gradlew test` for each test class — mutflow's JUnit 6 extension handles the multi-run model (baseline + mutation runs) internally
4. **`test-auditor`** parses JSON results + JUnit XML, calculates mutation score, identifies zombie test candidates, detects over-mocked tests
5. **`test-refactor-specialist`** generates improved test code for flagged issues

### Prerequisites

- Kotlin JVM project with Gradle
- mutflow plugin configured (`io.github.anschnapp.mutflow` in `build.gradle.kts`)
- Test classes annotated with `@MutFlowTest`

### mutflow architecture notes

- mutflow is **JVM-only** — no JS/Native/Android support in v1
- mutflow uses **compile-once meta-mutant**: all mutations injected at compile time, one active per run
- mutflow's **global synchronized lock** serializes mutation runs — parallel executors block-and-wait
- mutflow reports **aggregate verdicts per mutation** (Killed with first killer test, Survived, TimedOut) — not per-test-per-mutation matrices

### Issue tracking

Decisions and issues tracked in `.scratch/mutation-testing-omp/`. See `docs/agents/issue-tracker.md` for the tracking workflow.

### Dispatch

This skill is a thin wrapper — it spawns `test-quality-reviewer` via the `task` tool:

```
task with agent: "test-quality-reviewer", task: "Run mutation testing on [project path] with targets [targets]"
```
