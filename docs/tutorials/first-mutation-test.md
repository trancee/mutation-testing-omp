# Tutorial: Your first mutation test in 10 minutes

In this tutorial, we'll create a Kotlin project with mutation testing using mutflow. We will build a small `Calculator` class, write tests for it, and use mutation testing to find gaps in those tests. By the end we'll have a project that achieves **100% mutation coverage**.

This tutorial assumes no prior knowledge of mutation testing.

## Prerequisites

- Java 21 (or newer)
- Gradle 9.x
- Kotlin 2.4.x

If you have [SDKMAN](https://sdkman.io), install Gradle:

```bash
sdk install gradle 9.7
```

## Step 1: Create a new Kotlin project

We'll start from scratch. Create a project directory and run Gradle's init:

```bash
mkdir mutation-tutorial
cd mutation-tutorial
gradle init --type kotlin-application
```

Open the project in your IDE. You should see `build.gradle.kts` and `settings.gradle.kts` in the root, plus a `src/` directory with `main/` and `test/` subdirectories.

## Step 2: Add the mutflow plugin

Edit `settings.gradle.kts` to add the plugin management block:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "mutation-tutorial"
```

Then edit `build.gradle.kts` to apply the mutflow plugin:

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "1.0.5"
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}
```

## Step 3: Write the Calculator class

Create `src/main/kotlin/Calculator.kt`:

```kotlin
package example

import io.github.anschnapp.mutflow.MutationTarget

@MutationTarget
class Calculator {
    fun isPositive(x: Int): Boolean = x > 0

    fun add(a: Int, b: Int): Int = a + b

    fun divide(a: Int, b: Int): Int = a / b
}
```

The `@MutationTarget` annotation tells mutflow which classes to mutate. We use it on `Calculator` because it contains business logic (comparisons, arithmetic).

## Step 4: Write tests

Create `src/test/kotlin/CalculatorTest.kt`:

```kotlin
package example

import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@MutFlowTest
class CalculatorTest {
    private val calc = Calculator()

    @Test
    fun `isPositive returns true for positive numbers`() {
        assertTrue(MutFlow.underTest { calc.isPositive(5) })
    }

    @Test
    fun `isPositive returns false for negative numbers`() {
        assertFalse(MutFlow.underTest { calc.isPositive(-1) })
    }

    @Test
    fun `add returns correct sum`() {
        assertEquals(7, MutFlow.underTest { calc.add(3, 4) })
    }

    @Test
    fun `divide returns correct quotient`() {
        assertEquals(2, MutFlow.underTest { calc.divide(10, 5) })
    }
}
```

The `@MutFlowTest` annotation enables mutflow's JUnit 6 extension. Every call to business logic is wrapped in `MutFlow.underTest { }` so mutflow can inject mutations.

## Step 5: Run the tests

Run the test task:

```bash
gradle test
```

You'll see output like:

```
CalculatorTest > Run without mutations > isPositive returns true for positive numbers() PASSED
CalculatorTest > Mutation: (Calculator.kt:8) > → >= ... PASSED
CalculatorTest > Mutation: (Calculator.kt:8) 0 → 1 ... PASSED
...
```

All tests show `PASSED` — this is expected. During mutation runs, when a test catches a mutation, mutflow swallows the failure so it appears green. Check the summary at the bottom:

```
╔════════════════════════════════╗
║      MUTATION TESTING SUMMARY  ║
╠════════════════════════════════╣
║  Killed:  6  ✓                 ║
║  Survived: 0  ✓                ║
║  Timed out: 0  ✓               ║
╚════════════════════════════════╝
```

## Step 6: Find and fix surviving mutations

Not all mutations are caught by the first tests we wrote. For example, `isPositive` checks `x > 0` — the `0 → 1` mutation changes this to `x > 1`. Our test checks `isPositive(5)` (true under both original and mutant) and `isPositive(-1)` (false under both). Neither catches the mutation.

Add a boundary test:

```kotlin
@Test
fun `isPositive returns false for zero`() {
    assertFalse(MutFlow.underTest { calc.isPositive(0) })
}
```

This kills the `0 → -1` mutation (original: `0 > 0 = false`, mutant: `0 > -1 = true`). But the `0 → 1` mutation (mutant: `x > 1`) still survives because `isPositive(0)` is false under both `x > 0` and `x > 1`. Add one more:

```kotlin
@Test
fun `isPositive returns true for one`() {
    assertTrue(MutFlow.underTest { calc.isPositive(1) })
}
```

This kills `0 → 1` (original: `1 > 0 = true`, mutant: `1 > 1 = false`).

Re-run `gradle test`. The summary should now show all mutations killed:

```
╔════════════════════════════════╗
║      MUTATION TESTING SUMMARY  ║
╠════════════════════════════════╣
║  Killed:  7  ✓                 ║
║  Survived: 0  ✓                ║
║  Timed out: 0  ✓               ║
╚════════════════════════════════╝
```

## Summary

We created a Kotlin project with mutflow mutation testing. We wrote a `Calculator` with `@MutationTarget`, tests with `@MutFlowTest`, and found that the initial tests missed mutations at boundary values. By adding boundary tests (`isPositive(0)`, `isPositive(1)`), we killed all mutations and achieved **100% mutation coverage**.

For how to interpret mutation testing results and quality bands, see the [How to interpret mutation testing results](../how-to/interpret-results.md) guide.

To understand how mutflow works under the hood, see [About mutflow's compile-once meta-mutant](../explanation/mutflow-architecture.md).
