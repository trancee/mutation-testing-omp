# Research: Gradle Kotlin DSL init script compilation mechanism

**Ticket**: [01-research-gradle-kotlin-init-script](issues/01-research-gradle-kotlin-init-script.md)
**Date**: 2026-08-24
**Status**: resolved

## Question

Can a Gradle Kotlin DSL init script (or `apply(from = ...)` block in a `.gradle.kts` file) compile and use Kotlin source files from a custom directory like `.omp/mutation-results-src/`, and can `kotlinx-serialization` be used in that context?

## Findings

### Gradle Kotlin DSL scripts are compiled, not interpreted

Gradle Kotlin DSL scripts (`.gradle.kts`) are compiled during the build's configuration phase using Gradle's embedded Kotlin compiler. Classes defined in a script (e.g. `MutationResultsTask` in the current `mutation-results.gradle.kts`) are compiled as part of that script and are available within the script's evaluation scope.

**Source**: [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html) — "Gradle Kotlin DSL scripts are compiled by Gradle during the configuration phase of your build."

### Init scripts cannot see `buildSrc`

Classes declared in a Gradle initialization script (`init.gradle.kts`) are limited to the init-script classpath. `buildSrc` code is not available to init scripts. To use a custom Kotlin class in an init script, it must be published (as a JAR or pre-compiled script plugin) to a repository and declared via an `initscript { dependencies { classpath(...) } }` block.

**Source**: [Gradle Forums — Plugins and apply from in the Kotlin DSL](https://discuss.gradle.org/t/plugins-and-apply-from-in-the-kotlin-dsl/28662)

### `kotlinx-serialization` compiler plugin is NOT applied to script compilation

This is the critical constraint: Gradle Kotlin DSL scripts are compiled by the embedded Kotlin compiler, which does **not** apply the `kotlinx-serialization` compiler plugin. This means `@Serializable` annotations on data classes defined in a `.gradle.kts` script will **not** generate `serializer()` companion objects, and `Json.encodeToString()` / `Json.decodeFromString()` will fail at compilation or runtime.

To use `kotlinx-serialization` with `@Serializable` data classes, you need a **proper Kotlin compilation unit** — not a Gradle script.

### The standard pattern: `buildSrc/` with `kotlin-dsl` plugin

The Gradle-standard mechanism for sharing typed Kotlin between build scripts is a `buildSrc/` directory with the `kotlin-dsp` plugin applied. Classes and functions in `buildSrc/src/main/kotlin/` are automatically compiled and made available to all build scripts in the project. The `kotlinx-serialization` plugin can be applied to `buildSrc` to enable `@Serializable`.

**Source**: [Pre-compiled Script Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_precompiled.html)

### Options for `.omp/` context

| Option | Mechanism | kotlinx-serializable? | Pros | Cons |
|---|---|---|---|---|
| **buildSrc** in target project | Copy Kotlin source to `buildSrc/src/main/kotlin/` + `kotlin-dsl` plugin | Yes | Standard Gradle pattern, full IDE support, testable | Pollutes target project with buildSrc/ directory |
| **Published JAR** | Compile `.omp/` module as a JAR, publish to Maven local, add as classpath dependency | Yes | Clean separation, reusable across projects | Requires publish step, version management |
| **Inline in `.gradle.kts`** | Define data classes in the script file | No | No buildSrc pollution | Can't use @Serializable — must use manual JSON |

## Conclusion

**kotlinx-serialization is not feasible in a `.gradle.kts` apply(from) script.** The serialization compiler plugin is not applied during Gradle script compilation.

The viable approaches are:

1. **buildSrc with kotlin-dsl + kotlinx-serialization plugin** (recommended — standard Gradle pattern)
2. **Published JAR dependency** (cleaner but adds publish complexity)
3. **Inline data classes without kotlinx-serialization** (typed model but manual JSON — falls back to the "manual JSON from typed data classes" option the user didn't choose)

Given the user's explicit choice of "kotlinx.serialization with typed data classes" in the earlier grilling session, the **`buildSrc/` approach is the path forward**. The bootstrap script needs to generate a `buildSrc/` directory with the Kotlin source, `kotlin-dsl` plugin, and `kotlinx-serialization` plugin.

## Impact on the map

- Ticket 01 is resolved: **buildSrc is the mechanism**
- Ticket 02 (data model grilling) can proceed: the model uses `@Serializable` in `buildSrc/src/main/kotlin/`
- Ticket 03 (decouple parser) depends on buildSrc being set up
- Ticket 05 (bootstrap update) needs to generate buildSrc

## Assets

- [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Pre-compiled Script Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_precompiled.html)
- [Gradle Forums: Plugins and apply from in the Kotlin DSL](https://discuss.gradle.org/t/plugins-and-apply-from-in-the-kotlin-dsl/28662)
