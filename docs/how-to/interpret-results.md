# How to interpret and act on mutation testing results

Use this guide when you have a mutation testing run and want to act on the results.

## Prerequisites

- You've run `gradle test` (or `gradle mutationResults`) and mutflow generated output
- You have the mutation summary (console) or `mutation-results.json` (Gradle task)

## Step 1: Read the mutation summary

mutflow prints a summary after all runs:

```
╔════════════════════════════════════════════════════════════════╗
║                    MUTATION TESTING SUMMARY                    ║
╠════════════════════════════════════════════════════════════════╣
║  Total mutations discovered:   6                               ║
║  Tested this run:              6                               ║
║  ├─ Killed:                    4  ✓                            ║
║  ├─ Survived:                  1  ✗                            ║
║  └─ Timed out:                 1  ⏱                            ║
╚════════════════════════════════════════════════════════════════╝
```

- **Killed** (✓): A test caught the mutation — the test suite is strong for that code
- **Survived** (✗): No test caught the mutation — there's a test gap. Add a test.
- **Timed out** (⏱): The mutation likely caused an infinite loop. Add `// mutflow:ignore` to the affected line or add a timeout guard.

## Step 2: Calculate your mutation score

```
mutation score = killed / total
```

| Band | Score | Action |
|------|-------|--------|
| Excellent | >80% | Good coverage. Spot-check survivors. |
| Good | 60–80% | Add tests for the surviving mutations listed in the summary. |
| Fair | 30–60% | Significant test gaps. Prioritize boundary and edge case tests. |
| Poor | <30% | Rewrite or add substantial test coverage. |

## Step 3: Locate surviving mutations

Each survived mutation shows the source location and the operator change:

```
✗ (Calculator.kt:35) > → >=
    SURVIVED - no test caught this mutation!
```

- File: `Calculator.kt`, line 35
- Operator: `>` was mutated to `>=`

## Step 4: Fix surviving mutations

For each surviving mutation, add a boundary test that distinguishes the original from the mutant. For a complete workflow including common patterns per operator type and the trap feature, see [How to fix surviving mutations](fix-surviving-mutations.md).
(Skip ahead if you want the full workflow with patterns per operator type — this guide covers the essentials.)


```kotlin
// Original: x > 0,  mutant: x >= 0
// At x=0: original returns false, mutant returns true
assertFalse(calc.isValid(0))  // catches the > -> >= mutation
```

**Example**: `0 → 1` constant-boundary on line 23 (`x > 0`):

```kotlin
// Original: x > 0,  mutant: x > 1
// At x=1: original returns true, mutant returns false
assertTrue(calc.isPositive(1))  // catches the 0 -> 1 mutation
```

General strategy: **test the boundary value** where the operator change produces a different result.

## Step 5: Trap a mutation you're fixing

If you need to fix tests over multiple sessions, trap a survivor so it runs first every time:

```kotlin
@MutFlowTest(traps = ["(Calculator.kt:35) > → >="])
class CalculatorTest { ... }
```

Copy the display name from the survivor output. After fixing, remove the trap.

## Step 6: Handle infinite loops

If a mutation causes a timeout (⏱), mutflow suggests adding `// mutflow:ignore`:

```kotlin
fun processLoop(items: List<String>) {
    // mutflow:ignore  // loop condition mutation causes infinite loop
    for (item in items) {
        // ...
    }
}
```

## Confidence levels

Results are more reliable with more mutations:

| Confidence | Mutation count |
|------------|----------------|
| Low | <10 |
| Medium | 10–50 |
| High | 50+ |

With Low confidence, survivors may be false positives — verify carefully.
