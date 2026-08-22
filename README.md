# Mutation Testing OMP

A 5-agent mutation testing system for Kotlin (JVM-first) projects, powered by [mutflow](https://github.com/anschnapp/mutflow) as the underlying mutation engine and built on the [OMP](https://omp.org) agent/task/skill architecture.

This system ports [Scott-CC's](https://github.com/Scott-CC/mutation-testing-plugin) multi-agent orchestration to OMP, adapting from Scott-CC's per-mutant git worktree model to mutflow's compile-once meta-mutant architecture.

## Quick start

Bootstrap into any Kotlin project and run mutation testing in two commands:

```bash
# 1. Install the system (copies agents, skills, Gradle scripts, configures build)
./.omp/bootstrap-mutation-testing.sh /path/to/kotlin-project

# 2. Run mutation testing via the orchestrator
/mutation-test /path/to/kotlin-project
```

For a guided walkthrough, see the [tutorial](docs/tutorials/first-mutation-test.md).

## Setup

### Automated (recommended)

```bash
./.omp/bootstrap-mutation-testing.sh <project-path> [--kmp]
```

The script copies `.omp/agents/`, `.omp/skills/`, and `mutation-results.gradle.kts` into your project, adds the mutflow Gradle plugin, and configures `pluginManagement` in `settings.gradle.kts`. Use `--kmp` for Kotlin Multiplatform projects (mutflow targets the JVM source set).

### Manual

1. **Prerequisites**: Java 21+, Gradle 9.x+, Kotlin 2.4.x
2. **Copy files**: Copy `.omp/agents/` and `.omp/skills/mutation-test/` into your project root
3. **Add plugin management** (`settings.gradle.kts`):

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

4. **Add plugins and dependencies** (`build.gradle.kts`):

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "1.0.5"
}

apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.github.anschnapp.mutflow:mutflow-junit6:1.0.5")
}
```

> **Note**: mutflow is published to Maven Central only (not the Gradle Plugin Portal), so `pluginManagement` with `mavenCentral()` is required. The `mutflow-junit6` dependency provides the runtime engine; you must also add `junit-jupiter-api` and `junit-platform-launcher` as testImplementation.

5. **Annotate source**: Add `@MutationTarget` to business-logic classes and `@MutFlowTest` to test classes. The test-saboteur agent handles this automatically when you run `/mutation-test`.

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
| `test-saboteur` | Mutation targeting — adds `@MutationTarget`, `@MutFlowTest`, configures mutflow |
| `test-executor` | Test execution — runs `./gradlew mutationResults` |
| `test-auditor` | Results analysis — calculates score, finds zombies, detects over-mocked tests |
| `test-refactor-specialist` | Test improvement — proposes boundary tests for surviving mutations |

Orchestration: [ADR-002](docs/adr/0002-agent-structure-and-orchestration-model.md)

## Sample project

The `sample/` directory contains a reference Kotlin project with a `Calculator` class achieving **100% mutation coverage** (27/27 mutations killed, Excellent band).

## Known limitations (v2)

- Exception type mutations (no mutflow operator)
- Full per-test-per-mutation zombie detection matrix (mutflow tracks only first killer)
- KMP/JS/Native support (mutflow is JVM-only — the `--kmp` bootstrap flag targets JVM source sets only)

See [CONTEXT.md](CONTEXT.md) for details.
