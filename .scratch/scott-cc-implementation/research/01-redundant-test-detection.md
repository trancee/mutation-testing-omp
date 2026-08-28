# R1: Research — Redundant Test Group Detection

**Status:** Research findings — resolved
**Date:** 2026-08-26
**Author:** ResearchRedundantTestDetection (R1)
**Source files:** Scott-CC test-auditor (`citadelgrad/scott-cc/plugins/mutation-testing/agents/test-auditor.md`), OMP test-auditor (`.omp/agents/test-auditor.md`), OMP typed JSON module (`.omp/mutation-results-src/`), OMP Gradle task (`.omp/mutation-results.gradle.kts`), comparison matrix (`.scratch/scott-cc-comparison/research/02-comparison-matrix.md`)

---

## 1. Executive Summary

Scott-CC's redundant test group detection uses a **failure-signature algorithm**: each test is assigned a signature (the set of mutation IDs it failed for), tests with identical signatures are grouped, and groups exceeding a threshold of **>5 tests** are flagged as redundant.

**OMP's data model is sufficient to reconstruct failure signatures.** The `testKillerMatrix` field in OMP's `MutationResults` JSON (`Map<String, List<String>>` — test name → mutation source locations it killed) is functionally identical to Scott-CC's failure signature. For higher precision matching Scott-CC's per-mutation granularity, the `mutations` list with `killedByTests` arrays provides a composite key of `(sourceLocation, originalOperator, variantOperator)`.

**Recommended integration point: both the Kotlin typed module and the test-auditor agent.** The algorithmic grouping computation belongs in `MutationResultsParser` (reliable, unit-testable, produces JSON output); the semantic pattern description and recommendation belong in the test-auditor agent (requires LLM-level understanding).

---

## 2. Scott-CC Failure-Signature Algorithm

### 2.1 Source

Scott-CC test-auditor, section "### 3. Find Redundant Test Groups" (lines 106–127 of the source file).

### 2.2 Algorithm (verbatim from Scott-CC)

```python
# Algorithm
test_failure_signatures = {}

for test_name in all_tests:
    signature = []
    for result in test_results:
        if test_name in result['failures']:
            signature.append(result['mutation_id'])

    test_failure_signatures[test_name] = signature

# Group by signature
from collections import defaultdict
groups = defaultdict(list)

for test, sig in test_failure_signatures.items():
    groups[tuple(sig)].append(test)

# If >5 tests have same signature → redundant group
redundant_groups = [tests for sig, tests in groups.items() if len(tests) > 5]
```

### 2.3 Step-by-step explanation

| Step | What happens | Scott-CC data source |
|---|---|---|
| 1 | Enumerate all test names from the intersection of `test_outcomes` keys across all mutation results | `test_results[].test_outcomes` |
| 2 | For each test, build a **failure signature** — the ordered list of mutation IDs the test failed for (i.e., mutations the test caught/killed) | `test_results[].failures` (list of test names that failed per mutation) |
| 3 | Group tests by identical signature (tuple comparison) | In-memory `defaultdict` |
| 4 | Filter groups where `len(tests) > 5` | Threshold: **strictly greater than 5** |

### 2.4 Key parameters

| Parameter | Value |
|---|---|
| **Threshold** | >5 tests per group (strictly greater than 5) |
| **Signature type** | Ordered list of mutation IDs (becomes a tuple for grouping key) |
| **Grouping key** | Exact equality of the signature tuple |
| **Empty signatures** | Tests that never failed for any mutation (zombies) get an empty signature — they are **not** flagged as redundant groups (they're caught by the separate zombie detection algorithm) |

### 2.5 Scott-CC output format for redundant groups

```json
"redundant_groups": [
  {
    "pattern": "Django model field validation",
    "tests": ["test_status_valid", "test_status_invalid", "..."],
    "count": 150,
    "failure_signature": ["mut-003", "mut-007"],
    "recommendation": "Consolidate into 1 parameterized test"
  }
]
```

The `pattern` and `recommendation` fields are **semantically generated** by the LLM auditor (describing the common test pattern and suggesting consolidation). The `tests`, `count`, and `failure_signature` fields are **algorithmically computed**.

### 2.6 Data flow in Scott-CC

Scott-CC's data model is **in-memory dict handoff** between agents:

1. **test-executor agents** (×15, one per mutation) each return a per-mutation result:
   ```json
   {
     "mutation_id": "mut-001",
     "test_results": {"total": 200, "passed": 195, "failed": 5},
     "test_outcomes": {"tests/test_stripe.py::test_retry_boundary": "failed"},
     "failures": [{"test": "test_retry_boundary", "error": "..."}]
   }
   ```
2. **test-auditor** receives the aggregated list of 15 per-mutation results and reconstructs failure signatures by scanning the `failures` arrays.

The auditor does **not** receive a pre-computed matrix — it builds signatures from the raw per-mutation `failures` lists by iterating all tests against all mutation results. This is an O(tests × mutations) scan.

---

## 3. OMP Data Model Analysis

### 3.1 OMP's typed JSON module

Location: `.omp/mutation-results-src/main/kotlin/io/omp/mutation/`

```
MutationResult.kt          // Per-mutation result data class
MutationResults.kt          // Top-level results container
MutationResultsParser.kt    // Pure parsing + matrix building functions
MutationResultsSerializer.kt // JSON serialization
```

### 3.2 MutationResult data class

```kotlin
@Serializable
data class MutationResult(
    val sourceLocation: String,         // e.g., "(Calculator.kt:7)"
    val originalOperator: String,        // e.g., ">"
    val variantOperator: String,          // e.g., ">="
    val result: MutationResultType,       // Killed, Survived, TimedOut
    val killedByTest: String? = null,     // legacy: first killer only
    val killedByTests: List<String> = [], // ALL tests that killed this mutation
)
```

Key point: `killedByTests` (multi-killer tracking, upstream v1.1.1+) captures **all** tests that kill each mutation, not just the first. This is the OMP equivalent of Scott-CC's per-mutation `failures` list.

### 3.3 MutationResults data class

```kotlin
@Serializable
data class MutationResults(
    val generatedAt: Long,
    val mutationScore: Double,
    val qualityBand: QualityBand,
    val confidence: ConfidenceLevel,
    val totalMutations: Int,
    val killed: Int,
    val survived: Int,
    val timedOut: Int,
    val testMethods: List<String>,                    // all test method names
    val testKillerMatrix: Map<String, List<String>>,  // test → mutation source locations it killed
    val mutations: List<MutationResult>,              // per-mutation results with killedByTests
)
```

### 3.4 How OMP builds the testKillerMatrix

From `MutationResultsParser.kt`, lines 128–136:

```kotlin
fun buildTestKillerMatrix(mutations: List<MutationResult>): Map<String, List<String>> {
    val testKillerMatrix = mutableMapOf<String, MutableList<String>>()
    mutations.forEach { m ->
        m.killedByTests.forEach { testName ->
            testKillerMatrix.getOrPut(testName) { mutableListOf() }.add(m.sourceLocation)
        }
    }
    return testKillerMatrix
}
```

This iterates all killed mutations and, for each killer test, adds the mutation's source location to that test's entry in the map. The result is: **test name → list of mutation source locations it killed**.

### 3.5 Sufficiency Assessment

**Verdict: Sufficient.** OMP's data model can fully reconstruct failure signatures. Two approaches are available:

#### Approach A: Using `testKillerMatrix` directly (simplest)

The `testKillerMatrix` is functionally identical to Scott-CC's failure signature:

| Scott-CC | OMP |
|---|---|
| `test_name → [mutation_id, mutation_id, ...]` | `test_name → [sourceLocation, sourceLocation, ...]` |
| mutation_id is unique per mutation | sourceLocation identifies the mutation's location |

The failure signature for a test is simply `testKillerMatrix[test_name].toSet()`. Grouping is:

```kotlin
val groups = mutableMapOf<Set<String>, MutableList<String>>()
testKillerMatrix.forEach { (testName, killedLocations) ->
    val signature = killedLocations.toSet()
    if (signature.isNotEmpty()) {  // exclude zombies (empty signature)
        groups.getOrPut(signature) { mutableListOf() }.add(testName)
    }
}
val redundantGroups = groups.filter { it.value.size > 5 }
```

**Limitation**: `sourceLocation` alone does not uniquely identify a mutation if multiple operators are applied at the same line (e.g., `> → >=` and `>= → >` at `(Calculator.kt:7)`). The `testKillerMatrix` would list `Calculator.kt:7` once or twice depending on how many mutations at that location the test killed. For grouping purposes, converting to `Set` collapses duplicates, which means tests that kill different operators at the same line but not identical mutation sets could still be grouped together.

#### Approach B: Using `mutations` list + `killedByTests` (precise)

The `mutations` list provides full per-mutation granularity. Each `MutationResult` has a composite key of `(sourceLocation, originalOperator, variantOperator)`, and `killedByTests` lists all killer tests. The failure signature can be reconstructed with full precision:

```kotlin
val testSignatures = mutableMapOf<String, MutableSet<String>>()
mutations.forEach { mutation ->
    if (mutation.result == MutationResultType.Killed) {
        val mutationKey = "${mutation.sourceLocation}:${mutation.originalOperator}->${mutation.variantOperator}"
        mutation.killedByTests.forEach { testName ->
            testSignatures.getOrPut(testName) { mutableSetOf() }.add(mutationKey)
        }
    }
}

val groups = mutableMapOf<Set<String>, MutableList<String>>()
testSignatures.forEach { (testName, signature) ->
    if (signature.isNotEmpty()) {
        groups.getOrPut(signature.toSet()) { mutableListOf() }.add(testName)
    }
}
val redundantGroups = groups.filter { it.value.size > 5 }
```

This approach gives exactly Scott-CC's granularity: each mutation is uniquely identified, and the signature captures the full set of mutations each test caught.

**Trade-off**: Approach A is simpler and uses the already-computed `testKillerMatrix` (no need to re-scan the `mutations` list). Approach B is more precise but requires traversing the `mutations` list. For practical redundancy detection, Approach A is sufficient — the edge case of multiple operators at the same source location producing different test outcomes is rare and would not significantly affect redundancy conclusions.

#### Approach C: Hybrid (recommended)

Use `testKillerMatrix` for the signature (it's already computed and in the JSON), but when a redundant group is found, cross-reference with the `mutations` list to provide the precise `failure_signature` in the output (listing the specific mutation operator pairs, not just source locations).

### 3.6 Scott-CC vs OMP data flow comparison

| Aspect | Scott-CC | OMP |
|---|---|---|
| **Executor count** | 1 per mutation (15 executors) | 1 per test class (fewer, but mutflow's internal loop handles mutations) |
| **Data handoff** | In-memory dict between agents | Typed Kotlin JSON artifact (`mutation-results.json`) |
| **Per-mutation data** | `failures` list (tests that failed per mutation) | `killedByTests` array (all tests that killed each mutation) |
| **Per-test data** | Reconstructed by auditor from `failures` arrays | Pre-computed `testKillerMatrix` (test → source locations killed) |
| **Mutation identity** | Unique ID (`mut-001`) | Composite key: `(sourceLocation, originalOperator, variantOperator)` |
| **Data sufficiency for signatures** | Yes — `failures` arrays contain all needed info | Yes — `testKillerMatrix` + `mutations.killedByTests` contain all needed info |

---

## 4. Integration Point Recommendation

### 4.1 Options considered

| Option | Where | Pros | Cons |
|---|---|---|---|
| **A** | Test-auditor agent only (in-memory from JSON) | No code changes; follows Scott-CC's pattern of auditor-side analysis | LLM agent may produce incorrect set operations on large matrices; not unit-testable; no reusable JSON output |
| **B** | Kotlin module only (Gradle task computes and outputs JSON) | Reliable, unit-testable, follows OMP's typed-module pattern; JSON available to any consumer | Loses LLM-level semantic pattern description; requires JSON schema extension |
| **C** | **Both** (Kotlin module computes groups + agent describes patterns) | Reliable computation + semantic recommendations; follows separation of concerns; JSON available to all consumers | Most work; requires changes in 3 files |

### 4.2 Recommended: Option C (both)

#### C.1 Kotlin module changes (`.omp/mutation-results-src/`)

**File: `MutationResults.kt`** — Add `RedundantGroup` data class and field:

```kotlin
@Serializable
data class RedundantGroup(
    @SerialName("tests") val tests: List<String>,
    @SerialName("count") val count: Int,
    @SerialName("failureSignature") val failureSignature: List<String>,
)

// Add to MutationResults:
@SerialName("redundantGroups") val redundantGroups: List<RedundantGroup> = emptyList(),
```

**File: `MutationResultsParser.kt`** — Add detection function:

```kotlin
fun detectRedundantTestGroups(
    mutations: List<MutationResult>,
    threshold: Int = 5  // matches Scott-CC's >5 threshold
): List<RedundantGroup> {
    val testSignatures = mutableMapOf<String, MutableSet<String>>()

    mutations.forEach { mutation ->
        if (mutation.result == MutationResultType.Killed) {
            val mutationKey = "${mutation.sourceLocation}:${mutation.originalOperator}->${mutation.variantOperator}"
            mutation.killedByTests.forEach { testName ->
                testSignatures.getOrPut(testName) { mutableSetOf() }.add(mutationKey)
            }
        }
    }

    val groups = mutableMapOf<Set<String>, MutableList<String>>()
    testSignatures.forEach { (testName, signature) ->
        if (signature.isNotEmpty()) {
            groups.getOrPut(signature.toSet()) { mutableListOf() }.add(testName)
        }
    }

    return groups.filter { it.value.size > threshold }
        .map { (signature, tests) ->
            RedundantGroup(
                tests = tests.sorted(),
                count = tests.size,
                failureSignature = signature.sorted(),
            )
        }
        .sortedByDescending { it.count }
}
```

**File: `MutationResultsParser.kt`** — Update `assembleResults()` to call the new function.

**File: `mutation-results.gradle.kts`** — No changes needed (thin adapter already calls `assembleResults()`).

**Tests**: Add unit tests for `detectRedundantTestGroups()` covering: exact-signature grouping, threshold boundary (5 vs 6 tests), exclusion of zombie tests (empty signature), precision with same-location different-operator mutations, empty input, sorting, and backward compatibility (existing tests still pass).

#### C.2 Test-auditor agent prompt (`.omp/agents/test-auditor.md`)

1. **Document the algorithm**: Add a "Find Redundant Test Groups" section describing the failure-signature algorithm, threshold (>5), and how it reads `redundantGroups` from the JSON.
2. **Read pre-computed data**: The agent reads `redundantGroups` from `mutation-results.json` (computed by the Kotlin module).
3. **Generate semantic output**: For each group, the agent generates:
   - `pattern`: A human-readable description of the common test pattern (e.g., "Django model field validation")
   - `recommendation`: Concrete suggestion (e.g., "Consolidate into 1 parameterized test")
4. **Include in output report**: Add `redundant_groups` to the auditor's JSON output.

#### C.3 Rationale for splitting between Kotlin and agent

OMP already follows this separation for zombie detection: the `testKillerMatrix` is **computed** in the Kotlin module (data preparation), while the **detection** (finding tests not in the matrix) is done by the agent. For redundant groups, the split is:

- **Kotlin module**: Algorithmic grouping computation (set operations, threshold filtering, sorting) — deterministic, unit-testable, language-appropriate.
- **Test-auditor agent**: Semantic pattern description and recommendation generation — requires understanding the test source code, naming conventions, and context.

This is cleaner than Scott-CC's approach, where the auditor does everything in Python (including the set grouping, which is error-prone for an LLM on large matrices). OMP's typed module provides reliability for the computational part, while the LLM handles what it does best: semantic reasoning.

### 4.4 Backward compatibility

- Adding `redundantGroups` with a default of `emptyList()` to `MutationResults` is backward-compatible: existing JSON consumers that don't use the field will ignore it, and `encodeDefaults = true` ensures it appears in output.
- The field name follows OMP's existing camelCase convention (`testKillerMatrix`, `mutationScore`, etc.).

---

## 5. Gap Summary

| Scott-CC feature | OMP current state | What's needed |
|---|---|---|
| Failure-signature algorithm (>5 threshold) | Not present | Add `detectRedundantTestGroups()` to `MutationResultsParser` |
| `redundant_groups` in audit output | Not present | Add `RedundantGroup` data class + field to `MutationResults`, include in JSON |
| Pattern/recommendation generation | Not present (no redundant detection at all) | Document algorithm in test-auditor agent prompt; agent generates `pattern` + `recommendation` |
| Unit tests for the algorithm | N/A | Add tests in `MutationResultsParserTest.kt` |

**Effort estimate**: S–M (small-medium). The algorithm is ~25 lines of Kotlin. The main work is adding the data class, the function, updating `assembleResults()`, and writing unit tests. The agent prompt update is trivial.

---

## 6. Algorithm Specification for Implementation

```
INPUT:
  mutations: List<MutationResult>  // each has sourceLocation, originalOperator,
                                    // variantOperator, result, killedByTests
  threshold: Int = 5               // Scott-CC uses >5 (strictly greater than)

OUTPUT:
  List<RedundantGroup> sorted by count descending, each containing:
    - tests: sorted list of test names sharing the signature
    - count: number of tests in the group
    - failureSignature: sorted list of mutation key strings
    - (pattern and recommendation added by test-auditor agent)

ALGORITHM:
  1. For each mutation where result == Killed:
     a. Compute mutation_key = "${sourceLocation}:${originalOperator}->${variantOperator}"
     b. For each testName in mutation.killedByTests:
        - Add mutation_key to testSignatures[testName]
  2. For each test in testSignatures:
     a. signature = testSignatures[testName].toSet()
     b. If signature is non-empty (exclude zombies):
        - Add testName to groups[signature]
  3. For each group where len(tests) > threshold:
     - Create RedundantGroup(tests=sorted(tests), count=len(tests),
       failureSignature=sorted(signature))
  4. Sort results by count descending
  5. Return list

EDGE CASES:
  - Empty input → empty list
  - No killed mutations → empty list
  - All tests have empty signatures (all zombies) → empty list (no redundant groups)
  - Multiple operators at same source location → distinguished by operator in key
  - Ties in count → sorted alphabetically by first test name
```
