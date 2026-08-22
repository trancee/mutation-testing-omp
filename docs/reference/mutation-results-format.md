# Mutation results JSON format

This reference describes the structured output produced by the `mutationResults` Gradle task for the test-auditor agent.

## File location

```
<project>/build/reports/mutation-results.json
```

## JSON schema

| Field | Type | Description |
|-------|------|-------------|
| `generatedAt` | number | Unix timestamp (milliseconds) when the results were generated |
| `mutationScore` | number | Fraction of mutations killed (0.0–1.0) |
| `qualityBand` | string | Excellent / Good / Fair / Poor (see quality bands table) |
| `confidence` | string | Low / Medium / High (based on mutation count) |
| `totalMutations` | number | Total mutations discovered |
| `killed` | number | Mutations caught by at least one test |
| `survived` | number | Mutations not caught by any test |
| `timedOut` | number | Mutations that caused infinite loops |
| `testMethods` | array[string] | All test method names from JUnit XML |
| `testKillerMatrix` | object | Map: test displayName → array of mutation `sourceLocation` strings it killed. Enables full per-test-per-mutation zombie detection. |
| `mutations` | array[object] | Per-mutation details |

### mutations[].sourceLocation

File and line of the mutated code, e.g. `Calculator.kt:8`.

### mutations[].originalOperator

The original operator or value before mutation, e.g. `>`, `0`, `100`, `&&`.

### mutations[].variantOperator

The mutated operator or value, e.g. `>=`, `1`, `101`, `||`.

### mutations[].result

One of `Killed`, `Survived`, `TimedOut`.

### mutations[].killedByTest

Name of the first test that caught the mutation (JUnit display name). `null` if the mutation survived. Kept for backward compatibility.

### mutations[].killedByTests

Array of ALL test display names that caught the mutation. `null` if the mutation survived. The mutflow fork records every test that fails during a mutation run, not just the first.

**Example** (from the `Calculator` sample after adding `validateInput`):

```json
{
  "generatedAt": 1787405656101,
  "mutationScore": 1.0,
  "qualityBand": "Excellent",
  "confidence": "Medium",
  "totalMutations": 31,
  "killed": 31,
  "survived": 0,
  "timedOut": 0,
  "testMethods": ["testIsPositive()", "testIsPositiveBoundary()", "testPositiveNumbers()", ...],
  "testKillerMatrix": {
    "testIsPositive()": ["Calculator.kt:24", "Calculator.kt:24", "Calculator.kt:24", "Calculator.kt:24"],
    "testIsPositiveBoundary()": ["Calculator.kt:24", "Calculator.kt:24", "Calculator.kt:24", "Calculator.kt:24"],
    "testValidateInput()": ["Calculator.kt:52", "Calculator.kt:52", "Calculator.kt:52", "Calculator.kt:52"]
  },
  "mutations": [
    {
      "sourceLocation": "Calculator.kt:24",
      "originalOperator": ">",
      "variantOperator": ">=",
      "result": "Killed",
      "killedByTest": "testIsPositive()",
      "killedByTests": ["testIsPositive()", "testIsPositiveBoundary()", "testPositiveNumbers()"]
    }
  ]
}
```

## Quality bands

| Band | Score |
|------|-------|
| Excellent | >80% |
| Good | 60–80% |
| Fair | 30–60% |
| Poor | <30% |

## Confidence levels

| Level | Mutation count |
|-------|---------------|
| Low | <10 |
| Medium | 10–50 |
| High | 50+ |
