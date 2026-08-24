# Tutorial: Bootstrap mutation testing into an existing Kotlin project

In this tutorial, we'll take an existing Kotlin project and add mutation testing to it. We will run a bootstrap script that installs all the necessary files, configure mutflow, annotate our code, and run our first mutation test. By the end, we'll have mutation testing running on our existing code.

This tutorial assumes you have a Kotlin JVM project with Gradle. If you don't, see the [first mutation test tutorial](first-mutation-test.md) instead.

## Prerequisites

- An existing Kotlin JVM project with `build.gradle.kts` and `settings.gradle.kts`
- Java 21 (or newer)
- The OMP mutation testing system installed (this repo, cloned to a known location)

## Step 1: Run the bootstrap script

We'll use the bootstrap script to copy the .omp files and configure Gradle. From your project root:

```bash
# Replace with the path to this mutation-testing repo
OMP_MUTATION_DIR="/path/to/mutation-testing"

"$OMP_MUTATION_DIR/.omp/bootstrap-mutation-testing.sh" .
```

We'll see output like:

```
Bootstrapping mutation testing into: .
Mode: JVM

Copying .omp agents, skills, and scripts...
Setting up typed mutation-results module (buildSrc)...
  Created buildSrc/ with typed MutationResults module
Configuring settings.gradle.kts...
  Added pluginManagement block
Configuring build.gradle.kts...
  Added mutflow plugin
  Applied mutation-results.gradle.kts
  Added JUnit 6 + mutflow-junit6 dependencies
  Added mutflow configuration

✅ Bootstrap complete!
```

The script modified our `settings.gradle.kts` and `build.gradle.kts`, and copied the `.omp/` directory (agents, skills, scripts, and the typed results module) into our project.

## Step 2: Verify the Gradle setup

Open `build.gradle.kts`. We should see:

1. The mutflow plugin in the `plugins` block:

```kotlin
plugins {
    id("io.github.anschnapp.mutflow") version "1.0.5"
    // ... existing plugins
}
```

2. The mutation-results script applied:

```kotlin
apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
```

3. JUnit 6 dependencies added:

```kotlin
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.github.anschnapp.mutflow:mutflow-junit6:1.0.5")
}
```

4. The mutflow configuration block:

```kotlin
mutflow {
    enabled = true
}
```

5. A `buildSrc/` directory with the typed mutation-results module (data classes and pure parser functions).

## Step 3: Annotate business logic with @MutationTarget

We need to tell mutflow which classes to mutate. Let's say our project has a `UserService` class with business rules:

```kotlin
package com.example.service

class UserService {
    fun isEligible(age: Int, isActive: Boolean): Boolean {
        return age >= 18 && isActive
    }
}
```

We add `@MutationTarget`:

```kotlin
package com.example.service

import io.github.anschnapp.mutflow.MutationTarget

@MutationTarget
class UserService {
    fun isEligible(age: Int, isActive: Boolean): Boolean {
        return age >= 18 && isActive
    }
}
```

The `@MutationTarget` annotation marks classes that contain business logic — comparisons, arithmetic, boolean logic, return values — the code mutflow's operators will mutate.

## Step 4: Annotate tests with @MutFlowTest

Open our existing test file. We'll add the `@MutFlowTest` annotation and wrap business logic calls in `MutFlow.underTest { }`:

Before:
```kotlin
package com.example.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UserServiceTest {
    private val service = UserService()

    @Test
    fun `isEligible returns true for adult active users`() {
        assertTrue(service.isEligible(25, true))
    }
}
```

After:
```kotlin
package com.example.service

import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@MutFlowTest
class UserServiceTest {
    private val service = UserService()

    @Test
    fun `isEligible returns true for adult active users`() {
        assertTrue(MutFlow.underTest { service.isEligible(25, true) })
    }
}
```

We wrap the call to the `@MutationTarget` instance in `MutFlow.underTest { }` so mutflow can inject mutations. We don't wrap the `assertTrue` itself — only the call to the code under test.

For more on this pattern, see [how to fix surviving mutations](../how-to/fix-surviving-mutations.md).

## Step 5: Run mutation testing

Run the `mutationResults` task:

```bash
gradle mutationResults
```

We'll see the mutflow summary at the bottom:

```
╔════════════════════════════════╗
║      MUTATION TESTING SUMMARY  ║
╠════════════════════════════════╣
║  Killed:  2  ✓                 ║
║  Survived: 1  ✗                ║
║  Timed out: 0  ✓               ║
╚════════════════════════════════╝
```

If some mutations survived, we can add boundary tests to kill them — see the [interpret results](../how-to/interpret-results.md) guide for details.

## Summary

We bootstrapped the OMP 5-agent mutation testing system into our existing Kotlin project. The bootstrap script handled file copying and Gradle configuration. We annotated our business logic with `@MutationTarget` and our tests with `@MutFlowTest`, wrapping calls in `MutFlow.underTest { }`. Our first mutation test run shows the results.
