# How to fix surviving mutations

Use this guide when your mutation testing run shows survived mutations that your tests didn't catch.

## Prerequisites

- You've run `gradle test` or `gradle mutationResults` and have results
- At least one mutation survived (shown as ✗ in the summary)

## Workflow

1. Find surviving mutations in the output
2. Identify the source location and operator change
3. Add a boundary test that distinguishes original from mutant
4. Re-run to confirm the mutation is killed

## Step 1: Identify surviving mutations

mutflow's summary shows survivors:

```
║  ✗ (Calculator.kt:35) > → >=                                    ║
║      SURVIVED - no test caught this mutation!                   ║
```

- **Source location**: `Calculator.kt:35` — the file and line of the mutated code
- **Operator change**: `>` → `>=` — the original operator and the mutated variant

## Step 2: Understand the mutation

Read the source at the flagged line:

```kotlin
// Calculator.kt:35
fun isValid(x: Int): Boolean = x > 0 && x < 100
```

The `>` was mutated to `>=`. Under the mutant, `isValid(0)` returns `0 >= 0 = true` instead of `0 > 0 = false`. Your existing tests don't test `x = 0`, so they pass under both original and mutant.

## Step 3: Add a boundary test

Add a test assertion at the boundary value where the operator change produces a different result:

```kotlin
// Original: 0 > 0 = false,  Mutant: 0 >= 0 = true
assertFalse(MutFlow.underTest { calc.isValid(0) })
```

This assertion fails under the mutant (assertFalse expects false, but mutant returns true), killing it.

## Step 4: Use traps for persistent survivors

If you're iterating on a fix over multiple sessions, trap the survivor so it runs first every time:

```kotlin
@MutFlowTest(traps = ["(Calculator.kt:35) > → >="])
class CalculatorTest { ... }
```

Copy the display name from the survivor output. After the mutation is killed, remove the trap.

## Common patterns for each operator type

### Relational comparison (`>` → `>=`, `<` → `<=`)

Test the exact boundary value. For `x > 0`, test `x = 0` (false under original, true under `>`→`>=` mutant).

### Constant boundary (`0` → `1`, `100` → `99`)

Test the value just above and below the constant. For `x > 0` with the `0 → 1` mutant, test `x = 1` (true original, false mutant) — `x = 0` alone doesn't work (both are false).

### Boolean logic (`&&` → `||`, `==` → `!=`)

Test all branches. For `a && b`, test: true/true, true/false, false/true, false/false.

### Arithmetic (`+` → `-`, `*` → `/`)

Test neutral elements: for `a + b`, test with 0 (`a + 0 = a`); for `a * b`, test with 1 (`a * 1 = a`) and 0 (`a * 0 = 0`).

### Boolean return (`return true` → `return false`)

Add assertions on both truthy and falsy return values — the mutation flips whatever the method returns.

## Verify

Re-run `gradle test` or `gradle mutationResults`. The summary should show the previously surviving mutations as killed (✓). If new mutations survive, repeat the workflow.

## When to stop

- All mutations are killed (100% score)
- Surviving mutations are confirmed false positives (e.g., the mutation has no meaningful behavior change in your domain)
- You reach your target mutation score threshold (e.g., 80% for "Excellent" band)
