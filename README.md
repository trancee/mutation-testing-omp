# Mutation Testing OMP

A 5-agent mutation testing system for Kotlin (JVM-first) projects, powered by [mutflow](https://github.com/anschnapp/mutflow) as the underlying mutation engine and built on the [OMP](https://omp.org) agent/task/skill architecture.

This system ports [Scott-CC's](https://github.com/Scott-CC/mutation-testing-plugin) multi-agent orchestration and LLM-guided semantic mutations to OMP, adapting from Scott-CC's per-mutant git worktree model to mutflow's compile-once meta-mutant architecture.

## Setup

1. **Prerequisites**: Java 21+, Gradle 9.x+, Kotlin 2.4.x
2. **Add agents**: Copy `.omp/agents/` into your project — OMP auto-discovers agents by their `name` field in frontmatter
3. **Add skill**: Copy `.omp/skills/mutation-test/` into your project — provides the `/mutation-test` command
4. **Configure mutflow**: Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "1.0.5"
}
apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))

> **Note**: mutflow is published to Maven Central only (not the Gradle Plugin Portal). Add to `settings.gradle.kts`:
> ```kotlin
> pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
> ```
> Also add `testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")` — mutflow-junit6 provides the engine at runtime only.

5. **Annotate source**: Add `@MutationTarget` to business-logic classes and `@MutFlowTest` to test classes (see [tutorial](docs/tutorials/first-mutation-test.md))
6. **Run**: Execute `/mutation-test <project-path>` — the orchestrator dispatches saboteur → executor → auditor → refactorer

## Quick start

Run mutation testing on any Kotlin JVM project:

```
/mutation-test [project path] [--targets <test-class-pattern>]
```

See the [tutorial](docs/tutorials/first-mutation-test.md) for a step-by-step guide.

## Documentation

| Need | Doc |
|------|-----|
| Get started learning | [Tutorial: Your first mutation test](docs/tutorials/first-mutation-test.md) |
| Interpret test results | [How-to: Interpret mutation testing results](docs/how-to/interpret-results.md) |
| Fix surviving mutations | [How-to: Fix surviving mutations](docs/how-to/fix-surviving-mutations.md) |
| Understand mutflow | [About mutflow's architecture](docs/explanation/mutflow-architecture.md) |
| JSON output format | [Reference: mutation-results.json](docs/reference/mutation-results-format.md) |
| Domain concepts | [CONTEXT.md](CONTEXT.md) |
| Design decisions | [docs/adr/0001](docs/adr/0001-use-mutflow-as-mutation-engine.md), [docs/adr/0002](docs/adr/0002-agent-structure-and-orchestration-model.md) |

## Architecture

| Agent | Role |
|-------|------|
| `test-quality-reviewer` | Orchestrator — coordinates the pipeline |
| `test-saboteur` | Mutation targeting — adds `@MutationTarget`, `@MutFlowTest` |
| `test-executor` | Test execution — runs `./gradlew test` |
| `test-auditor` | Results analysis — calculates score, finds zombies |
| `test-refactor-specialist` | Test improvement — proposes boundary tests |

Orchestration: [ADR-002](docs/adr/0002-agent-structure-and-orchestration-model.md)

## Sample project

The `sample/` directory contains a reference Kotlin project with a `Calculator` class achieving **100% mutation coverage** (27/27 mutations killed).

## Known limitations (v2)

- Exception type mutations (no mutflow operator)
- Full per-test-per-mutation zombie detection matrix (mutflow tracks only first killer)
- KMP/JS/Native support (mutflow is JVM-only)

See [CONTEXT.md](CONTEXT.md) for details.
