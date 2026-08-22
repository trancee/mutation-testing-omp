# Mutation Testing OMP

A 5-agent mutation testing system for Kotlin (JVM-first) projects, powered by [mutflow](https://github.com/anschnapp/mutflow) and built on the [OMP](https://omp.sh/) agent/task/skill architecture.

This ports [Scott-CC's](https://github.com/Scott-CC/mutation-testing-plugin) multi-agent orchestration to OMP, adapting from per-mutant git worktrees to mutflow's compile-once meta-mutant approach.

## Quick start

```bash
# 1. Install the system into your Kotlin project
./.omp/bootstrap-mutation-testing.sh /path/to/kotlin-project

# 2. Run mutation testing
/mutation-test /path/to/kotlin-project
```

Prerequisites: Java 21+, Gradle 9.x, Kotlin 2.4.x.

See the [documentation index](docs/index.md) for tutorials, how-to guides, and reference material.

## Sample project

The `sample/` directory contains a reference Kotlin project with a `Calculator` class at **100% mutation coverage** (27/27 mutations killed, Excellent band). Run `gradle mutationResults` in `sample/` to reproduce.

## Known limitations (v2)

mutflow's current limitations:

- **Exception types**: No mutflow operator exists for exception type mutations.
- **Zombie detection**: mutflow tracks only the first killer per mutation, not a full per-test-per-mutation matrix.
- **Kotlin Multiplatform**: mutflow is JVM-only. The `--kmp` bootstrap flag targets JVM source sets only.

See [CONTEXT.md](CONTEXT.md) for details.
