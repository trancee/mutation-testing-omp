---
Type: research
Status: resolved
Blocked by:
---

## Question

Can a Gradle Kotlin DSL init script (or `apply(from = ...)` block in a `.gradle.kts` file) compile and use Kotlin source files from a custom directory like `.omp/mutation-results-src/`? 

Specifically:

1. **Init script mechanism**: Can an `init.gradle.kts` placed in `gradle/init.d/` (or applied via `--init-script`) define a Kotlin class or function that's then callable from a `.gradle.kts` build script? If so, what's the exact mechanism — `extra` properties, `import` statements, or `apply()` of a pre-compiled script?

2. **kotlinx-serialization availability**: Can `org.jetbrains.kotlinx:kotlinx-serialization-json` be added as a classpath dependency via the init script so that `@Serializable` data classes can be defined in the same Kotlin compilation unit?

3. **Alternative: buildSrc**: If init scripts can't compile Kotlin source, can a `buildSrc/` module be generated during bootstrap that contains the typed data model? (This would pollute the target project but is a known-Gradle pattern.)

4. **Alternative: precompiled script plugin**: Can the typed module be delivered as a precompiled Kotlin script plugin (`.gradle.kts` with classes defined inline)?

The answer to this question determines whether the entire deepening approach is feasible in-process, or whether we need to fall back to a `buildSrc/` convention plugin or a separate JAR dependency.
## Answer

**Resolved**: Gradle Kotlin DSL scripts cannot use the `kotlinx-serialization` compiler plugin. The embedded Kotlin compiler that compiles `.gradle.kts` files does not apply serialization. Therefore, `@Serializable` data classes **cannot** be defined inside a `.gradle.kts` script.

The viable mechanism is **`buildSrc/` with the `kotlin-dsl` plugin** (option 3 in the question). The bootstrap script must generate a `buildSrc/` directory containing:
- Kotlin source with `@Serializable` data classes
- `buildSrc/build.gradle.kts` with `kotlin-dsl` + `kotlinx-serialization` plugins
- `kotlinx-serialization-json` as a dependency

The `.omp/mutation-results.gradle.kts` script is replaced by a thin Gradle adapter that imports the types from `buildSrc`.

Full findings: [research/01-findings.md](research/01-findings.md)

## Assets
