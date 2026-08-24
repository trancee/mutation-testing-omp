---
Type: research
Status: open
Blocked by:
---

## Question

Can a Gradle init script programmatically modify an existing project's `build.gradle.kts` — specifically, add a plugin to the `plugins {}` block, add dependencies, and add `apply(from = ...)` directives — without resorting to text-based `sed` manipulation?

### Background

The current `bootstrap-mutation-testing.sh` (lines 102–187) uses `sed -i.bak` and `grep` to inject mutflow configuration into the target project's `build.gradle.kts`. This approach has several fragilities:

1. **macOS vs Linux `sed -i`**: macOS `sed` requires a backup suffix (`sed -i.bak`), GNU `sed` doesn't
2. **Column-0 assumptions**: `grep -q '^plugins {'` and `grep -q '^}$'` fail when the file is indented or the closing brace is nested
3. **No programmatic API**: There's no way to tell if the plugin was actually added correctly — the script just checks if the string exists
4. **KMP complexity**: Appending dependencies at EOF for KMP but inserting after `dependencies {` for JVM is inconsistent

### Specific questions

1. **Plugin injection**: Gradle's `PluginManager` API can apply plugins at runtime, but does it work for plugins declared in `pluginManagement`? Can an init script use `project.pluginManager.apply("io.github.anschnapp.mutflow")` to add the mutflow plugin without modifying the `plugins {}` block text?

2. **Dependency injection**: Can an init script use `project.dependencies.add("testImplementation", "group:artifact:version")` programmatically? Does this work for `jvmTestImplementation` in KMP projects?

3. **Apply directive**: Can an init script apply a Gradle script from `.omp/mutation-results.gradle.kts` programmatically, or does it need to be injected into the build file text?

4. **Idempotency**: If the init script is applied on every build (not just bootstrap), will the programmatic additions be idempotent? Or do we need to check state?

5. **Init script delivery**: What are the cross-platform options for delivering an init script?
   - `gradle init.d/` directory (auto-loaded by Gradle)
   - `.gradle/init.gradle.kts` in the project root
   - `--init-script` flag on `gradle` command (requires modifying the test-executor's `gradle` invocation)

6. **Tradeoff**: Is there value in an init script that runs on every build (persistent) vs. one that runs once during bootstrap (ephemeral) vs. a one-time build file rewrite?
