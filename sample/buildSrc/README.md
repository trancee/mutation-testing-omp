# `sample/buildSrc` reference

The bootstrap script generates this directory. It contains the typed `MutationResults` data classes and parser functions used by `mutation-results.gradle.kts`.

The bootstrap script derives the Kotlin version in `build.gradle.kts` from the target project's Kotlin Gradle plugin version.

Do not edit these generated files directly. Regenerate them from the repository root:

```bash
.omp/bootstrap-mutation-testing.sh sample
```

See [How to set up mutation testing manually](../../docs/how-to/manual-setup.md) for the corresponding manual setup.
