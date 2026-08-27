# R2 Research: Auto-Generated Refactored Kotlin Test Code

**Status:** Research findings — resolved
**Date:** 2026-08-26
**Author:** ResearchAutoRefactoring (R2)
**Source files:** Scott-CC `plugins/mutation-testing/agents/test-refactor-specialist.md`, `.omp/agents/test-refactor-specialist.md`, `.omp/agents/test-auditor.md`, `.omp/agents/test-executor.md`, `.omp/agents/test-quality-reviewer.md`, `.omp/skills/mutation-test/SKILL.md`, `plugins/mutation-testing/commands/mutation-test.md`, `plugins/mutation-testing/agents/test-auditor.md`, `plugins/mutation-testing/agents/test-quality-reviewer.md`, `.omp/mutation-results-src/` (buildSrc typed module), `.scratch/scott-cc-comparison/research/02-comparison-matrix.md`, `.scratch/scott-cc-comparison/domain-model.md`

---

## 1. Summary

Scott-CC and OMP both conceptualize the same four refactoring actions (consolidate redundant tests, remove zombies, add edge cases, replace over-mocked with integration tests). The gap is **execution, not category**: Scott-CC auto-generates production-ready full test files and can auto-apply them; OMP's test-refactor-specialist only produces suggestions. The OMP agent description itself already calls for "full refactored test file content" — the gap is that no code-generation mechanism or pipeline wiring has been implemented.

The key technical decision is straightforward: **JUnit 5 `@ParameterizedTest`** is the correct Kotlin test framework (it is already a dependency in OMP's bootstrap), **full file content** is the recommended output format (matches the agent contract and Scott-CC's proven approach), and the consume path from the test-auditor's `testKillerMatrix` and `surviving_mutations` is well-defined but requires redundancy derivation logic that Scott-CC's auditor produces natively.

---

## 2. Scott-CC: How Full Test File Generation Works

### 2.1 Workflow (5 steps)

1. **Read existing test file** — the agent reads the test source to understand framework, imports, fixtures, and style.
2. **Analyze structure** — identify test framework, naming conventions, shared patterns.
3. **Generate refactored code** — for each action: consolidate (extract common pattern → parameterized test), remove (zombie with diff + explanation), add (edge-case tests following existing style), replace over-mocked (swap mocks for integration tests).
4. **Create complete refactored file** — emit the **entire** test file as a single output, with a module-level docstring documenting changes, metrics, and rationale.
5. **Generate git diff** — produce a full before/after diff so the user can review deletions, consolidations, and additions.

### 2.2 Output JSON Contract

```json
{
  "refactored_test_code": "... full test file ...",
  "changes": {
    "removed": ["test_name", ...],
    "consolidated": [{"from": ["test_a", ...], "to": "test_fn", "type": "parameterized"}],
    "added": ["test_boundary_at_3", ...]
  },
  "metrics": {
    "old_test_count": 200,
    "new_test_count": 20,
    "estimated_mutation_score": 0.85
  },
  "diff": "... git diff ...",
  "recommendations": ["..."],
  "warnings": ["..."]
}
```

### 2.3 Estimation Formulas

**Mutation score improvement** (conservative): each new edge-case test catches ~1.5 additional mutations; estimated score = (currentCaught + newTests × 1.5) / totalMutations.

**Execution time reduction**: parameterized tests share setup/teardown overhead. old_time = oldCount × (avgSetup + avgTest); new_time = newCount × (avgSetup + avgTest); speedup = old_time / new_time.

### 2.4 Auto-Apply: `--auto-approve`

The `/mutation-test` command (command contract, `plugins/mutation-testing/commands/mutation-test.md`) passes `auto_approve` to the orchestrator. When `--auto-approve` is present, the orchestrator applies the refactoring proposal without a second confirmation. **Critically, `--auto-approve` never permits deleting tests that the audit did not classify as zombie or redundant** — zombie deletion always requires explicit approval.

### 2.5 Framework-Specific Patterns (Python/JS)

| Framework | Pattern | Example |
|---|---|---|
| pytest | `@pytest.mark.parametrize("field,value,expected", [...])` | `@pytest.mark.parametrize("status", ["active", "canceled", ...])` |
| unittest | `@parameterized.expand([...])` | Decorator-based, class must extend `unittest.TestCase` |
| Jest/Vitest | `describe.each([{...}, ...])("label", ({status, expected}) => {...})` | Callback-based table-driven |

---

## 3. OMP: Current State (Suggestions Only)

### 3.1 Agent Contract

The OMP `test-refactor-specialist.md` (agent contract, `.omp/agents/test-refactor-specialist.md`) already declares the output format as **"Return the full refactored test file content"** plus a list of changes and rationale. This means the *intent* matches Scott-CC — the gap is that no code-generation mechanism is wired into the pipeline. The agent runs as the final phase of the `test-quality-reviewer` orchestrator.

### 3.2 Constraints

- Does NOT run tests (test-executor's job)
- Does NOT modify production source (test files only)
- Does NOT create mutations (test-saboteur's job)
- Focus on mutated classes identified by the auditor

### 3.3 Refactoring Actions (same categories as Scott-CC)

1. **Zombie test candidates** — review each: false positive (doesn't exercise mutated path) vs true zombie (should have caught but didn't).
2. **Over-mocked tests** (>3 mocks) — can mocks be replaced with real implementations?
3. **Surviving mutations** — identify which test SHOULD have caught it; add boundary/edge/negation tests.
4. **Consolidate redundant tests** — if multiple tests cover the same path, consolidate + add missed edge cases.
5. **Add edge cases** — Scott-CC's 5 mutation strategies mapped to test improvements (boundary, return values, boolean logic, arithmetic).

### 3.4 No Code Generation Yet

Comparison matrix §3.1 (row 3.1) confirms: **Output type** — Scott-CC produces production-ready full file content; OMP produces "suggestions only — no code auto-generation." Gap direction: Scott-CC-only. Effort to close: **L** (code generator + apply mechanism).

Missing sub-features per the matrix:

| Matrix Row | Scott-CC | OMP | Gap | Effort |
|---|---|---|---|---|
| 3.1 | Full file content | Suggestions only | Scott-CC-only | L |
| 3.2 | `--auto-approve` | No auto-apply | Scott-CC-only | M |
| 3.3 | Same action categories | Same categories | Both-have | N/A |
| 3.4 | Full git diff | No diff | Scott-CC-only | S |
| 3.5 | Metrics estimation | No metrics | Scott-CC-only | M |
| 3.6 | Language-specific patterns | Kotlin patterns | Both-have | N/A |
| 3.7 | AskUserQuestion approval | Manual apply gate | Both-have | N/A |

---

## 4. Kotlin Test Framework Analysis

### 4.1 OMP's Existing Test Stack

The OMP project's only Kotlin test files (in `buildSrc` — `.omp/mutation-results-src/test/kotlin/io/omp/mutation/`) use **JUnit 5 (Jupiter)**:

```kotlin
// From MutationResultsParserTest.kt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
```

The `buildSrc/build.gradle.kts` configures:
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
tasks.test { useJUnitPlatform() }
```

The `test-executor` agent confirms OMP uses JUnit 6 (`JUnit 6 extension handles the multi-run model internally`), and the `bootstrap-mutation-testing.sh` script installs JUnit 6 dependencies into the target project's `build.gradle.kts`.

### 4.2 Available Kotlin Test Frameworks

| Framework | Pros | Cons | OMP Status |
|---|---|---|---|
| **JUnit 5 `@ParameterizedTest`** | Already a dependency (JUnit 5/6 installed by bootstrap). `@ParameterizedTest` + `@CsvSource` / `@MethodSource` / `@ValueSource` / `@EnumSource` provide full parameterization. Mature, well-documented, Kotlin-friendly. Direct equivalent of pytest `@parametrize` and Jest `describe.each`. | Some verbosity with `@MethodSource` (need factory methods). | ✅ Ready — no new deps |
| **Kotest** | Idiomatic Kotlin DSL (`table`, `row`). `StringSpec`, `FunSpec`, `BehaviorSpec` with built-in parameterization. | Requires adding `kotest-runner-junit5` dependency. Not in OMP's bootstrap. Different test structure paradigm — existing JUnit 5 tests wouldn't mix naturally. | ❌ Not installed |
| **Spek** | Behavior-driven (given/when/then). | Not actively maintained (last release 2021). No native parameterized test support. Requires new dependency. | ❌ Not installed |

### 4.3 Recommendation: JUnit 5 `@ParameterizedTest`

**Rationale:**

1. **No new dependencies** — JUnit 5/6 is already installed by the bootstrap script and used in all existing OMP test files. Kotest and Spek would require new dependencies, new test runner configuration, and a paradigm shift.
2. **Direct conceptual mapping** — `@ParameterizedTest` with `@CsvSource` maps cleanly to Scott-CC's pytest `@parametrize` pattern:
   - pytest: `@pytest.mark.parametrize("status", ["active", "canceled", "trialing"])`
   - JUnit 5: `@ParameterizedTest @EnumSource` or `@CsvSource` variant
   - pytest: `@pytest.mark.parametrize("field,value,expected", [("status","active",True), ...])`
   - JUnit 5: `@ParameterizedTest @CsvSource("status,active,true", "status,canceled,true", ...)`
3. **Kotlin interoperability** — JUnit 5 annotations work natively in Kotlin. `@ParameterizedTest` + `@MethodSource` is the most Kotlin-idiomatic (can return `Stream<Arguments>` or use `@CsvSource` for simpler cases).
4. **Consistency** — all existing OMP tests use JUnit 5 Jupiter. Mixing Kotest or Spek would fragment the test suite.

**Kotlin JUnit 5 parameterization patterns:**

```kotlin
// Simple value source (like pytest's parametrize with single arg)
@ParameterizedTest
@ValueSource(strings = ["active", "canceled", "trialing", "past_due", "unpaid"])
fun `subscription status validation`(status: String) {
    val sub = Subscription(status = status)
    assertEquals(status, sub.status)
}

// CSV source (multiple parameters, like pytest table)
@ParameterizedTest
@CsvSource(
    "0, false",
    "1, false",
    "2, false",
    "3, true",   // Boundary — caught mut-001 (>= 3 → > 3)
    "4, true",
    "5, true",
)
fun `retry count boundary logic`(retryCount: Int, shouldRaise: Boolean) {
    if (shouldRaise) {
        assertThrows(MaxRetriesExceeded::class.java) {
            processPayment(retryCount = retryCount)
        }
    } else {
        assertTrue(processPayment(retryCount = retryCount).success)
    }
}

// Method source for complex objects
@ParameterizedTest
@MethodSource("boundaryCases")
fun `boundary condition test`(input: Int, expected: Boolean) {
    assertEquals(expected, isPositive(input))
}

companion object {
    @JvmStatic
    fun boundaryCases() = Stream.of(
        Arguments.of(0, false),
        Arguments.of(1, true),
        Arguments.of(-1, false),
    )
}
```

---

## 5. Recommended Output Format

### 5.1 Full File Content (not incremental patches)

**Recommendation:** Generate the **full refactored test file content**, following Scott-CC's proven approach.

**Rationale:**

1. **Agent contract already specifies it** — the OMP `test-refactor-specialist.md` says "Return the full refactored test file content." This aligns with Scott-CC.
2. **Kotlin's type safety makes incremental patches fragile** — patching Kotlin requires maintaining type-correct imports, proper `@ParameterizedTest`/`@MethodSource` factory methods, and companion object structure. A full file regeneration avoids partial-state errors.
3. **LLM agents generate full files naturally** — the test-refactor-specialist is an LLM that can read the original test file and emit a complete replacement. This is the same pattern Scott-CC uses.
4. **Diff can be derived** — once the full file is generated, a git diff is trivially produced by writing to a temp file and running `git diff`.

### 5.2 Structured Output Contract (matching Scott-CC)

The test-refactor-specialist should return:

```json
{
  "refactored_test_code": "... full Kotlin test file ...",
  "changes": {
    "removed": [{"test": "testName", "file": "path", "line": 47, "reason": "zombie — never caught any mutation"}],
    "consolidated": [{"from": ["testA", "testB", ...], "to": "testParameterized", "type": "parameterized", "count": N}],
    "added": [{"test": "testBoundaryAt3", "mutation_location": "(Calculator.kt:7)", "mutation_caught": "> → >=", "rationale": "..."}]
  },
  "metrics": {
    "old_test_count": 200,
    "new_test_count": 20,
    "reduction_percentage": 90,
    "old_mutation_score": 0.23,
    "estimated_new_mutation_score": 0.85
  },
  "diff": "... git diff ..."
}
```

### 5.3 Diff Generation Approach

Since the test-refactor-specialist operates as an agent in the OMP harness (not via Claude Code's Edit tool), diff generation should be handled by:

1. The agent writes the refactored file content to a temporary path.
2. A `git diff` between the original and temp file produces the diff.
3. The diff is included in the structured output for the user to review.

This mirrors Scott-CC's `diff` field in the output JSON (matrix §3.4, row 3.4).

---

## 6. Integration Approach: Consuming Audit Results

### 6.1 What the Test-Auditor Produces (OMP)

The OMP `test-auditor.md` (`.omp/agents/test-auditor.md`) outputs a JSON report with:

| Field | Type | Description |
|---|---|---|
| `mutation_score` | Double | killed / total |
| `quality_band` | Enum | Excellent/Good/Fair/Poor |
| `confidence` | Enum | Low/Medium/High |
| `total_mutations` | Int | |
| `killed` / `survived` / `timed_out` | Int | |
| `surviving_mutations` | List[String] | e.g. `"(Calculator.kt:5) > → >="` |
| `zombie_test_candidates` | List[String] | Test method names |
| `over_mocked_tests` | List[Object] | `{"method": "...", "mock_count": N}` |
| `test_killer_matrix` | Map<String, List<String>> | test name → mutation source locations killed |
| `recommendations` | List[String] | |

### 6.2 What Scott-CC's Auditor Produces (for comparison)

Scott-CC's test-auditor (`.mp/agents/test-auditor.md`, lines 1–300+) adds two fields that OMP's auditor lacks:

- **`redundant_groups`** — tests that always fail together (same `failure_signature`). Each group includes the test list, count, and recommendation. This is the **primary input** for the consolidation refactoring action.
- **`missing_coverage`** — per surviving mutation: type (boundary/return/boolean/arithmetic), line number, original code, suggestion, and which mutation survived. This feeds directly into the "add edge case tests" action.

### 6.3 How OMP's Refactor Specialist Must Consume Audit Results

#### 6.3.1 Deriving Redundant Groups from `test_killer_matrix`

OMP's auditor does **not** output `redundant_groups` directly. Instead, the refactor specialist must derive redundancy from the `test_killer_matrix`:

```
Algorithm:
1. For each test in test_killer_matrix, its "signature" = the set of mutation source locations it killed.
2. Group tests whose signatures are identical (or one is a subset of another's).
3. Groups with >5 tests (matching Scott-CC's threshold) → redundant groups eligible for consolidation.
4. For each group, recommend consolidating into a single parameterized test, preserving the union of mutation-killing coverage.
```

This is O(n²) for signature comparison (n = number of test methods). For typical projects with 50–200 tests, this is trivial. The `test_killer_matrix` already contains exactly the data needed — Scott-CC derives the same thing from `test_outcomes` per mutation, but the matrix is the inverse view and equally sufficient.

**Dependency on R1:** The `feature/redundant-test-groups` research (R1) investigated implementing this detection *in the auditor*. If R1's recommendation is to add `redundant_groups` to the auditor's JSON output, the refactor specialist receives it pre-computed. If not, the refactor specialist derives it from `test_killer_matrix` (always available). Either way, the refactor specialist needs the matrix.

#### 6.3.2 Cross-Referencing Zombies with Surviving Mutations

OMP's `zombie_test_candidates` is a flat list of test names. To generate targeted improvements:

```
For each zombie test candidate:
1. Read the test source file to find the test method.
2. Read the production source to find what code it exercises.
3. Cross-reference with surviving_mutations: find mutations whose source locations
   fall within the code paths the zombie test *should* have exercised.
4. Generate a strengthened version of the test with stronger assertions or edge-case inputs.
```

Scott-CC's auditor provides richer context (`mutations_it_should_have_caught` per zombie), but OMP's refactor specialist can derive the same by reading source files — which it must do anyway to generate Kotlin test code.

#### 6.3.3 Consuming Over-Mocked Tests

OMP's `over_mocked_tests` (`{"method": "...", "mock_count": N}`) maps directly to Scott-CC's. The refactor specialist reads the test file, identifies the `mockk()`/`@MockK`/Mockito `mock()` calls (threshold: >3 per matrix §2.6 row 2.6), and generates an integration-test variant using real implementations where feasible.

#### 6.3.4 Consuming Surviving Mutations

OMP's `surviving_mutations` (e.g., `"(Calculator.kt:5) > → >="`) gives source location + operator change. The refactor specialist:

1. Reads the production source at that location to understand the logic.
2. Generates a Kotlin test that exercises the boundary/mutated path.
3. For `>` → `>=` mutations, adds a test at the exact boundary value.
4. For `<` → `<=` mutations, adds the symmetric boundary test.

This mirrors Scott-CC's `missing_coverage` with `type: boundary_condition` and `suggestion` fields.

---

## 7. Recommended Apply Mechanism

### 7.1 Output Contract to Orchestrator

The test-refactor-specialist should **output the full refactored file content** (not write to the production test file directly). The `test-quality-reviewer` orchestrator then:

1. Presents the full refactored file + diff + changes manifest to the user.
2. If `--auto-approve` is set (planned as T4, matrix §4.3 row 8.3): apply without second confirmation, **but never auto-delete zombie/redundant tests without approval** (Scott-CC's safety principle, command contract lines 52–55).
3. If not auto-approved: ask the user to accept or refuse. Only on acceptance does the orchestrator write the file.

### 7.2 Writing the Refactored File

When the user/orchestrator approves, the refactored test file is written using the `write` tool (full file content). The test-refactor-specialist agent has `write` in its tools list (`.omp/agents/test-refactor-specialist.md` line 4), so it can write directly if the orchestrator delegates write authority.

### 7.3 Post-Apply Verification

Per OMP's constraints, the test-refactor-specialist does NOT run tests. After refactoring is applied:

- The `test-quality-reviewer` orchestrator should dispatch `test-executor` agents to re-run mutation testing on the refactored test class.
- This verifies the estimated mutation score improvement (from §5.2 metrics).

---

## 8. Key Findings Summary

| Decision Point | Recommendation | Rationale |
|---|---|---|
| **Output format** | Full refactored test file content | (1) Agent contract already specifies it; (2) Kotlin type safety makes incremental patches fragile; (3) matches Scott-CC's proven approach; (4) diff is trivially derived |
| **Test framework** | JUnit 5 `@ParameterizedTest` | Already a dependency (JUnit 5/6 in bootstrap); no new deps; direct mapping to pytest `@parametrize` and Jest `describe.each`; consistent with existing OMP tests |
| **Apply mechanism** | Agent outputs full file + diff + manifest; orchestrator gates via `--auto-approve` | Separates generation from application; `--auto-approve` controls auto-apply; zombie/redundant deletion always requires explicit approval (Scott-CC principle) |
| **Consume audit results** | Derive redundant groups from `test_killer_matrix`; cross-ref zombies with `surviving_mutations`; read source files for context | OMP's auditor doesn't output `redundant_groups` or `missing_coverage` directly, but `test_killer_matrix` + `surviving_mutations` provide equivalent data; source reading is needed anyway for Kotlin code generation |

### 8.1 Dependencies on Other Research Tickets

- **R1 (redundant test detection):** If R1 recommends adding `redundant_groups` to the auditor's JSON output, the refactor specialist receives it pre-computed and skips derivation from `test_killer_matrix`. If R1 recommends NOT adding it, the refactor specialist derives it (algorithm in §6.3.1). Either way, the refactor specialist depends on the `test_killer_matrix` being present in the audit JSON.
- **R3 (execution gap reporting):** The refactor specialist should not refactor test classes flagged with execution gaps (ERROR/INVALID_MUTATION from executor). These gaps indicate environment issues, not test quality issues. The `test-executor.md` (OMP) notes mutflow's JUnit extension handles multi-run internally; execution gaps in OMP's model map to executor timeouts or Gradle failures, not Scott-CC's worktree/syntax-error gaps.

### 8.2 Remaining Questions for the Decision Ticket (05-decide-auto-refactoring)

1. Should the refactor specialist write the refactored file directly (via `write` tool), or should it return content for the orchestrator to apply? — **Default: return content; let orchestrator handle writes** (matches Scott-CC's `refactored_test_code` field + `--auto-approve` contract).
2. Should `redundant_groups` be added to the auditor's JSON output (requires R1's decision), or derived in the refactor specialist? — **Default: derive from `test_killer_matrix`; upgrade to auditor output if R1 recommends it.**
3. Should metrics estimation use Scott-CC's formulas verbatim? — **Yes** for test count reduction; mutation score estimation should be conservative (§2.3).
