---
Type: task
Status: resolved
Blocked by: 01, 03
---

## Question

How do we update `bootstrap-mutation-testing.sh` to wire the typed Kotlin module and kotlinx-serialization into new target projects?

### Current bootstrap behavior

The script copies `.omp/` (agents, skills, `mutation-results.gradle.kts`), then uses `sed` to inject:
1. `id("io.github.anschnapp.mutflow") version "1.1.1"` into `plugins {}`
2. `io.github.anschnapp.mutflow:mutflow-junit6:1.1.1` into `dependencies {}`
3. `apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))` after the first `}`

### Required changes

1. Copy the new typed Kotlin source (e.g. `.omp/mutation-results-src/`) into the `.omp/` directory of the target project
2. Add `org.jetbrains.kotlinx:kotlinx-serialization-json:<version>` to the target project's `dependencies {}` (testImplementation or implementation, depending on how the module is compiled — see ticket 01)
3. Replace the `sed` injection of `apply(from = ...)` with the correct mechanism for the typed module (depends on ticket 01's answer — init script, buildSrc, or precompiled plugin)
4. Ensure the `mutationResults` task name and `build/reports/mutation-results.json` output path remain unchanged

### Constraints

- Must remain backward-compatible with existing projects that have already been bootstrapped
- Must work for both JVM and KMP target projects

## Answer

**Resolved**: The bootstrap script was updated with two changes:

1. **Step 1 (copy)**: Added `cp -r "$SCRIPT_DIR/mutation-results-src" "$target_dir/"` to copy the typed Kotlin module alongside the existing `.omp/` files.

2. **Step 3b (new)**: Added a buildSrc generation step that copies `.omp/mutation-results-src/main/kotlin/` into `<project>/buildSrc/src/main/kotlin/` and `.omp/mutation-results-src/test/kotlin/` into `<project>/buildSrc/src/test/kotlin/`, then copies the `build.gradle.kts` template (with `kotlin-dsl` + `kotlinx-serialization` plugins) to `<project>/buildSrc/build.gradle.kts`.

The existing `apply(from = ...)` sed injection is **retained** — the thin `mutation-results.gradle.kts` adapter still needs to be applied to the project to register the `mutationResults` task. The typed model lives in buildSrc, the thin adapter lives in `.omp/mutation-results.gradle.kts`.

Verified: `gradle mutationResults` in the sample project produces correct JSON output (31/31 killed, Excellent, Medium confidence) with the typed model imported from buildSrc.

## Assets
## Assets
