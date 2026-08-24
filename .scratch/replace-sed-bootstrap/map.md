# A wayfinder map: Replace sed-based bootstrap with structured Gradle init script

## Destination

Replace the fragile `sed`-based modifications in `bootstrap-mutation-testing.sh` (Step 3: lines 102–187) with a structured Gradle init script that programmatically adds the mutflow plugin, mutation-results apply, JUnit dependencies, and mutflow configuration to the target project. The init script must be idempotent, cross-platform (BSD sed → Gradle API), and handle arbitrary `build.gradle.kts` structures.

## Notes

- **Current approach**: `bootstrap-mutation-testing.sh` uses `sed -i.bak` and `grep` to inject lines into `build.gradle.kts`. This works for the sample project but breaks when:
  - The `plugins {}` block isn't at column 0 (indented or nested)
  - `dependencies {}` has no preceding `^dependencies` at column 0 (e.g. inside `kotlin { }`)
  - `^}$` matches a nested block closing brace before the top-level one
  - macOS vs Linux `sed -i` syntax differences (`.bak` suffix vs no suffix)
  - Multiple Kotlin plugin versions in a KMP project (only `kotlin("jvm")` detected)
- **Gradle init script mechanism**: `gradle init.d/` or `--init-script` runs an init script in a Gradle build session. Init scripts can use `settingsEvaluated` / `projectsLoaded` / `beforeProject` hooks to programmatically modify project build files.
- **Constraint**: The init script must be delivered via the bootstrap script (no separate JAR dependency). It lives in `.omp/gradle-init/` and is applied via `gradle --init-script` or copied to `gradle/init.d/`.
- **Target projects**: JVM and KMP Kotlin projects with `build.gradle.kts`

## Decisions so far

_(none yet — this is the frontier)_

## Not yet specified

- Can a Gradle init script modify an existing project's `plugins {}` block to add a new plugin? (Gradle doesn't support adding plugins programmatically via API — only via `plugins {}` block in build script or `pluginManagement` in settings)
- Can an init script add `dependencies {}` and `apply(from = ...)` to a build script programmatically?
- What's the cross-platform story for macOS vs Linux init script delivery?

## Open tickets (frontier)

- `issues/01-research-gradle-init-script.md` — **Research**: Can a Gradle init script programmatically modify `build.gradle.kts` (add plugins, dependencies, apply directives) without sed? What APIs are available?
- `issues/02-grill-structured-bootstrap.md` — **Grilling**: What's the target design? Init script that modifies in-memory model? Or init script that rewrites the build file via Kotlin AST? What's the tradeoff?
- `issues/03-task-rewrite-bootstrap.md` — **Task**: Replace sed with the chosen approach

## Out of scope

- Changing the mutflow plugin API or Gradle task structure
- Replacing the buildSrc generation (that's already structured — it copies typed Kotlin sources)
- Changing the `.omp/mutation-results.gradle.kts` task itself

## Status: frontier 🚧
