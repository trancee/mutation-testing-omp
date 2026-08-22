# Mutflow KMP Support and Compiler Plugin Internals Research

**Source:** mutflow repository at https://github.com/anschnapp/mutflow (cloned to /tmp/mutflow-repo)

**Version:** Latest from master branch (based on Kotlin 2.4.x compatibility shown in README)

---

## 1. KMP Target Support

**Answer:** Mutflow does **NOT** currently support Kotlin Multiplatform (KMP) targets beyond JVM. It is explicitly a JVM-only tool.

### Evidence:

**Gradle Plugin Implementation** (`mutflow-gradle-plugin/src/main/kotlin/io/github/anschnapp/mutflow/gradle/MutflowGradlePlugin.kt`):

```kotlin
override fun apply(target: Project) {
    debug("apply() called for project: ${target.name}")

    val extension = target.extensions.create("mutflow", MutflowExtension::class.java)
    extension.enabled.convention(
        target.providers.gradleProperty("mutflow.enabled")
            .map { it.toBoolean() }
            .orElse(true)
    )
    extension.targets.convention(emptyList())

    target.plugins.withId("org.jetbrains.kotlin.jvm") {  // <-- JVM-only check
        debug("  kotlin.jvm plugin detected, configuring...")
        target.afterEvaluate {
            if (extension.enabled.get()) {
                debug("  mutflow is enabled, configuring source sets and dependencies")
                configureSourceSets(target)
                addDependencies(target)
            } else {
                ...
            }
        }
        debug("  configuration complete")
    }
}
```

The plugin only checks for `org.jetbrains.kotlin.jvm` plugin - there is no check for `kotlin-multiplatform`, `kotlin-js`, `kotlin-native`, or other KMP plugins.

**README.md (lines 55-62)** explicitly states JVM compatibility only:

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "<latest-version>"
}
```

**Kotlin Version Compatibility Table** (README.md):

| mutflow | Kotlin |
|---------|--------|
| 1.1.0+ | 2.4.x |
| up to 1.0.3 | 2.2.x - 2.3.x |

### Dual-Compilation with KMP Source Set Hierarchy:

Mutflow uses a dual-compilation approach with a `mutatedMain` source set:

```kotlin
// From MutflowGradlePlugin.kt
private fun configureSourceSets(project: Project) {
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val mainSourceSet = sourceSets.getByName("main")
    
    // Create mutatedMain source set that mirrors main sources
    val mutatedMain = sourceSets.create(MUTATED_MAIN) { sourceSet ->
        sourceSet.java.srcDirs(mainSourceSet.java.srcDirs)
        sourceSet.resources.srcDirs(mainSourceSet.resources.srcDirs)
    }
    
    // mutatedMain needs same dependencies as main
    project.configurations.named("${MUTATED_MAIN}Implementation") {
        it.extendsFrom(project.configurations.getByName("implementation"))
    }
}
```

**Critical Issue for KMP:** The plugin copies `java.srcDirs` and `resources.srcDirs` from `main` to `mutatedMain`. However, KMP uses different source set hierarchies (`commonMain`, `jvmMain`, `jsMain`, `nativeMain`, etc.) with platform-specific sources. The current implementation would:

1. Only work with `main` source set (JVM-specific)
2. Not handle `commonMain` → `commonTest` or platform-specific source sets
3. Not understand KMP's expected source set naming conventions

### No KMP-Specific Configurations Found:

Search of the codebase found **zero mentions** of:
- `multiplatform`
- `kmp`
- `jsMain`, `jsTest`
- `linuxX64`, `mingwX64`, `iosArm64`
- `commonMain`, `commonTest`
- `expect`/`actual`

### Conclusion:

Mutflow is **JVM-only** and would require significant changes to support KMP:
1. New source set handling for `commonMain`, `jvmMain`, etc.
2. Support for platform-specific compilation units
3. IR transformation logic compatible with K2 compiler for JS/Native targets

---

## 2. Compiler Plugin Architecture

### How MutflowIrTransformer Injects Mutation Points

The transformer implements `IrElementTransformerVoid` and visits IR nodes to inject `MutationRegistry.check()` calls at mutation points.

**Key transformation flow** (`MutflowIrTransformer.kt:1-912`):

```kotlin
class MutflowIrTransformer(
    private val pluginContext: IrPluginContext,
    private val callOperators: List<MutationOperator> = defaultCallOperators(),
    private val returnOperators: List<ReturnMutationOperator> = defaultReturnOperators(),
    private val functionBodyOperators: List<FunctionBodyMutationOperator> = defaultFunctionBodyOperators(),
    private val whenOperators: List<WhenMutationOperator> = defaultWhenOperators(),
    private val targetPatterns: List<String> = emptyList()
) : IrElementTransformerVoid()
```

**Injection Mechanism** (visitCall method, lines 229-244):

```kotlin
override fun visitCall(expression: IrCall): IrExpression {
    // First, transform children (bottom-up for nested expressions)
    val transformed = super.visitCall(expression) as IrCall

    // Only transform if we're in a @MutationTarget class and not suppressed
    if (!isInMutationTarget || isInSuppressedScope) {
        return transformed
    }
    if (isLineSuppressedByComment(transformed.startOffset)) {
        return transformed
    }

    val fn = currentFunction ?: return transformed
    return transformCallWithOperators(transformed, fn, callOperators)
}
```

**Operator Application** (excerpt from transformCallWithOperators, lines 800+):

```kotlin
private fun transformCallWithOperators(
    call: IrCall,
    containingFunction: IrSimpleFunction,
    operators: List<MutationOperator>
): IrExpression {
    ...
    when (call.origin) {
        in RelationalComparisonOperator.SUPPORTED_ORIGINS -> 
            // Wrap in when expression with check() calls
        else -> {
            // Try ArithmeticOperator, EqualitySwapOperator, etc.
        }
    }
}
```

### Full Mutation Operator Catalog

Defined in `MutflowIrTransformer.kt:74-94`:

```kotlin
fun defaultCallOperators(): List<MutationOperator> = listOf(
    RelationalComparisonOperator(),    // >, <, >=, <=
    ConstantBoundaryOperator(),        // 0 → 1, 1 → -1, etc.
    ArithmeticOperator(),              // +, -, *, /, %
    EqualitySwapOperator(),            // == ↔ !=
    BooleanInversionOperator()          // !flag, flag property
)

fun defaultReturnOperators(): List<ReturnMutationOperator> = listOf(
    BooleanReturnOperator(),           // return true → return false
    NullableReturnOperator()           // return x → return null
)

fun defaultFunctionBodyOperators(): List<FunctionBodyMutationOperator> = listOf(
    VoidFunctionBodyOperator()         // Body removal in void functions
)

fun defaultWhenOperators(): List<WhenMutationOperator> = listOf(
    BooleanLogicOperator()             // && ↔ ||
)
```

### Adding New Operators

New operators are added by implementing the appropriate interface:

**MutationOperator interface** (`MutationOperator.kt:17-60`):

```kotlin
interface MutationOperator {
    fun matches(call: IrCall): Boolean
    fun variants(call: IrCall, context: MutationContext): List<MutationOperator.Variant>
    fun originalDescription(call: IrCall): String
    
    data class Variant(
        val description: String,
        val createExpression: () -> IrExpression
    )
}
```

**Adding to catalog** (`MutflowIrTransformer.kt:74-80`):

```kotlin
fun defaultCallOperators(): List<MutationOperator> = listOf(
    RelationalComparisonOperator(),
    ConstantBoundaryOperator(),
    ArithmeticOperator(),
    EqualitySwapOperator(),
    BooleanInversionOperator()
    // Add new operator here
)
```

### IR Transformation Pattern Example (RelationalComparisonOperator.kt):

```kotlin
class RelationalComparisonOperator : MutationOperator {
    override fun matches(call: IrCall): Boolean {
        return call.origin in SUPPORTED_ORIGINS  // GT, LT, GTEQ, LTEQ
    }
    
    override fun variants(call: IrCall, context: MutationContext): List<Variant> {
        return when (call.origin) {
            IrStatementOrigin.GT -> listOf(
                createVariant(">=", left, right, greaterOrEqualFn, context.builder),
                createVariant("<", left, right, lessFn, context.builder)
            )
            // ... other operators
        }
    }
    
    override fun originalDescription(call: IrCall): String {
        return when (call.origin) {
            IrStatementOrigin.GT -> ">"
            IrStatementOrigin.LT -> "<"
            // ...
        }
    }
}
```

---

## 3. Runtime Registry

### How MutFlow.underTest + @MutFlowTest Work at Runtime

**Compile-once, Meta-Mutant Approach:**

1. **Compilation Phase:** Compiler plugin injects nested `when` expressions with `MutationRegistry.check()` calls

2. **Production Code Transformation** (DESIGN.md lines 35-54):

```kotlin
// Before
fun isPositive(x: Int): Boolean {
    return x > 0
}

// After (compiled)
fun isPositive(x: Int): Boolean {
    return when (MutationRegistry.check(
        pointId = "sample.Calculator_0",
        variantCount = 2,
        sourceLocation = "Calculator.kt:4",
        originalOperator = ">",
        variantOperators = ">=,<",
        occurrenceOnLine = 1
    )) {
        0 -> x >= 0
        1 -> x < 0
        else -> x > 0
    }
}
```

3. **Baseline Run (Run 0):** All tests execute normally, `MutationRegistry.check()` returns `null`, original code runs.

4. **Mutation Runs:** `ActiveMutation(pointId, variantIndex)` activates specific variant.

### Global Mutation Registry Discovery

**MutationRegistry.kt** (`mutflow-core/src/main/kotlin/io/github/anschnapp/mutflow/MutationRegistry.kt`):

```kotlin
object MutationRegistry {
    @Volatile
    private var currentSession: Session? = null
    private val lock = Any()
    
    fun check(
        pointId: String,
        variantCount: Int,
        sourceLocation: String,
        originalOperator: String,
        variantOperators: String,
        occurrenceOnLine: Int = 1
    ): Int? {
        val session = currentSession ?: return null
        
        // Register point if not seen in this session
        if (session.seenPointIds.add(pointId)) {
            session.discoveredPoints.add(
                DiscoveredPoint(
                    pointId = pointId,
                    variantCount = variantCount,
                    sourceLocation = sourceLocation,
                    originalOperator = originalOperator,
                    variantOperators = variantOperators.split(","),
                    occurrenceOnLine = occurrenceOnLine
                )
            )
        }
        
        // Check if this point is active
        val active = session.activeMutation ?: return null
        if (active.pointId == pointId) {
            return active.variantIndex
        }
        
        return null
    }
}
```

### Selection, Touch Count, and Run Management

**Session State** (`MutFlowSession.kt`):

```kotlin
class MutFlowSession {
    private val discoveredPoints = mutableMapOf<String, Int>() // pointId -> variantCount
    private val pointMetadata = mutableMapOf<String, PointMetadata>()
    private val touchCounts = mutableMapOf<String, Int>() // pointId -> count
    private val testedMutations = mutableSetOf<Mutation>()
    
    fun selectMutationForRun(run: Int): Mutation? {
        // 1. Return null if partial run or baseline had failures
        // 2. Resolve traps
        // 3. Select mutation using strategy
        return selectMutation(run)
    }
}
```

**Selection Strategies** (`MutFlow.kt:377-394`):

```kotlin
enum class Selection {
    PureRandom,       // Uniform random among untested mutations
    MostLikelyRandom, // Weighted by touch counts (1/touchCount)
    MostLikelyStable  // Lowest touch count, then alphabetical
}
```

**Shuffle Modes** (`MutFlow.kt:426-440`):

```kotlin
enum class Shuffle {
    PerRun,    // New seed each JVM/CI run
    PerChange  // Seed = hash(discoveredPoints) - stable until code changes
}
```

**Default Selection** (`MutFlowExtension.kt:109`):

```kotlin
val sessionId = MutFlow.createSession(
    selection = Selection.MostLikelyStable,
    shuffle = Shuffle.PerChange,
    ...
)
```

---

## 4. Semantic Mutation Feasibility

**Answer:** Mutflow is **restricted** to its predefined operator catalog. It does **not** support context-aware semantic mutations like Scott-CC's LLM-guided saboteur.

### Compile-Once Meta-Mutant Approach Limitations

The "mutant schemata" technique fundamentally compiles all mutations into the code at compile time, guarded by conditional branches. This means:

1. **Static Mutation Set:** All possible mutations must be known and defined at compile time. The compiler plugin generates a fixed set of variants for each operator.

2. **No Extension Point for Dynamic Mutations:**

The `MutationOperator` interface requires implementing `matches()` and `variants()` methods that return IR expressions. There is no runtime extension point for:
- Context-aware mutations that depend on runtime state
- LLM-guided semantic mutations
- Data-flow aware mutations

3. **Variant Generation is Static:**

```kotlin
// From MutationOperator.kt
data class Variant(
    val description: String,
    val createExpression: () -> IrExpression  // Must be pre-defined IR creation
)
```

Each variant description is a static string, and `createExpression` returns pre-compiled IR.

### No Semantic Mutation Extension Point

**Current extension model** (`MutationOperator.kt:24-37`):

```kotlin
interface MutationOperator {
    fun matches(call: IrCall): Boolean
    fun variants(call: IrCall, context: MutationContext): List<Variant>
    fun originalDescription(call: IrCall): String
}
```

The interface operates on IR nodes and must return complete IR expressions. There is no mechanism for:
- Registering mutation strategies that analyze program semantics
- Injecting mutations based on data-flow analysis at runtime
- Adding mutations that require cross-function context

### Implications for Scott-CC Porting:

To support Scott-CC's LLM-guided semantic mutations, mutflow would need:
1. A new operator type that can generate context-aware variants
2. Runtime injection mechanism (currently all mutations are pre-compiled)
3. Possibly a different architecture (e.g., runtime code generation instead of compile-time injection)

The current design makes this **incompatible** with Scott-CC's approach without significant architectural changes.

---

## 5. Parallel Execution

### Test Parallelization Handling

Mutflow uses **synchronization locks** to handle parallel test execution:

**MutationRegistry.kt (lines 19-22)**:

```kotlin
object MutationRegistry {
    @Volatile
    private var currentSession: Session? = null
    private val lock = Any()  // Global lock for thread safety
```

```kotlin
fun <T> withSession(
    activeMutation: ActiveMutation? = null,
    timeoutMs: Long = 0,
    block: () -> T
): Pair<T, SessionResult> {
    synchronized(lock) {  // Only one mutation session active at a time
        check(currentSession == null) { "Session already active" }
        currentSession = Session(activeMutation)
        try {
            val result = block()
            ...
        } finally {
            currentSession = null
        }
    }
}
```

**Key Point:** The `synchronized(lock)` ensures that **only one mutation session can be active at a time** across all test classes, even when running tests in parallel. This prevents race conditions but also serializes mutation runs.

### Mutation Ordering

Mutation selection is controlled by `Selection` and `Shuffle` modes:

**Selection Strategies** (`MutFlowSession.kt:380-403`):

```kotlin
private fun selectMostLikelyStable(mutations: List<Mutation>): Mutation {
    return mutations.minWith(
        compareBy(
            { touchCounts[it.pointId] ?: 0 },  // Lowest touch count first
            { it.pointId },
            { it.variantIndex }
        )
    )
}

private fun selectPureRandom(mutations: List<Mutation>, seed: Long): Mutation {
    val random = Random(seed)
    return mutations[random.nextInt(mutations.size)]
}

private fun selectMostLikelyRandom(mutations: List<Mutation>, seed: Long): Mutation {
    val weights = mutations.map { mutation ->
        val touchCount = touchCounts[mutation.pointId] ?: 1
        1.0 / touchCount  // Weight inversely proportional to touch count
    }
    // Weighted random selection
}
```

**Shuffle Modes** (`MutFlowSession.kt:405-410`):

```kotlin
val seed = when (shuffle) {
    Shuffle.PerRun -> getOrCreateSessionSeed() + run  // New seed each CI run
    Shuffle.PerChange -> computePointsHash() + run    // Stable until discovered points change
}
```

### Interaction with External Orchestrator

Mutflow's approach **does not easily integrate with external orchestrators** because:

1. **JUnit-dependent Lifecycle:** The `@MutFlowTest` annotation and `MutFlowExtension` rely on JUnit 6's `ClassTemplateInvocationContextProvider` pattern to generate multiple invocations.

2. **Per-Class Session Management:** Each test class gets its own session, managed by the JUnit extension:
   ```kotlin
   // MutFlowExtension.kt:66-71
   val sessionId = MutFlow.createSession(
       selection = Selection.MostLikelyStable,
       shuffle = Shuffle.PerChange,
       ...
   )
   ```

3. **No External API for Orchestration:** There is no mechanism to:
   - Request a specific mutation via external API
   - Receive callbacks for mutation completion
   - Integrate with external parallelization frameworks

### Parallel Execution Constraints

From DESIGN.md (lines 267-278):

```
### 2. Global Baseline and Run Model

Mutation testing operates at the **test class level** with a global registry:

1. **Run 0 (baseline)**: ALL test cases in the class execute first, discovering mutation points
2. **Run 1+**: ALL test cases execute with the **same mutation** active

...

- **Same mutation for all tests**: A run activates one mutation across the entire test suite
- **Global discovery**: Mutation points from all tests are merged into a single registry
- **Touch counting**: During baseline, we count how many tests touch each mutation point
- **Run limit**: Tests run up to N times (configured), or until all mutations are exhausted
```

---

## 6. Zombie Detection Compatibility

**Answer:** Mutflow provides **only aggregate survivor verdicts** at the mutation level, **not per-test outcomes** for each mutation run.

### Zombie Detection Definition

In mutation testing, a "zombie mutation" is one that survives the entire test suite - indicating the tests have gaps. Mutflow's approach inherently detects zombies but with different granularity than traditional tools.

### What Mutflow Provides

**MutationResult sealed class** (`MutFlowSession.kt:558-564`):

```kotlin
sealed class MutationResult {
    data class Killed(val testName: String) : MutationResult()  // Which test killed it
    data object Survived : MutationResult()                     // Zombie detected!
    data object TimedOut : MutationResult()                     // Likely infinite loop
}
```

**Summary Output** (`MutFlowSession.kt:517-591`):

```kotlin
fun getSummary(): MutationTestingSummary {
    val totalMutations = discoveredPoints
        .filter { isPointIncluded(it.key) }
        .values.sum()
    val tested = mutationResults.size
    val killed = mutationResults.count { it.value is MutationResult.Killed }
    val survived = mutationResults.count { it.value is MutationResult.Survived }
    ...
}
```

### Per-Test Pass/Fail Information

**Available:**
- For **killed mutations**: Which test killed it (captured in `Killed(testName)`)
- For **survived mutations**: All tests passed (no specific per-test breakdown)
- For **timed out**: Test failed with timeout exception

**Not Available:**
- Which specific test passed/failed for each mutation run
- Per-test analysis when multiple tests execute the same code path

### How Zombie Detection Works

From DESIGN.md (lines 283-309):

```
### 2. Global Baseline and Run Model

...

**Key principles:**
- **Same mutation for all tests**: A run activates one mutation across the entire test suite
- **Global discovery**: Mutation points from all tests are merged into a single registry
- **Touch counting**: During baseline, we count how many tests touch each mutation point
- **Run limit**: Tests run up to N times (configured), or until all mutations are exhausted

This means:
- We can determine if a mutation **survives the entire test suite**
- Mutations touched by fewer tests are identified as higher risk
- **Precise feedback: when a mutant survives, you know exactly which one**
```

### Zombie Mutation Reporting

**When a zombie is detected:**

1. **During mutation run:** If all tests pass with a mutation active, `didMutationSurvive()` returns `true`

2. **Exception thrown:** `MutantSurvivedException` is thrown with details:
   ```kotlin
   class MutantSurvivedException(
       val mutation: Mutation,
       val displayName: String = "${mutation.pointId}:${mutation.variantIndex}"
   ) : AssertionError(...)
   ```

3. **Summary shows zombies:**
   ```
   ║  ├─ Killed:                  N  ✓                           ║
   ║  ├─ Survived:                N  ✗                           ║
   ```

4. **Trap mechanism** helps debug zombies:
   ```kotlin
   @MutFlowTest(traps = ["(Calculator.kt:8) > → >="])
   ```

### Limitations for Zombie Analysis:

1. **No Per-Test Breakdown per Mutation:** When a mutation survives, you know the mutation but not which hypothetical test "should have" caught it.

2. **Global Verdict:** The verdict is "this mutation survived all tests" rather than "test X didn't catch mutation Y even though it touched the code."

3. **No Mutation-Per-Test Matrix:** Traditional tools like Pitest produce matrices showing which tests killed which mutations. Mutflow's design aggregates at the mutation level.

### Comparison with Scott-CC Expectations:

Scott-CC's approach with LLM-guided saboteurs likely needs:
- Per-mutation, per-test analysis
- Ability to attribute zombie mutations to specific test methods
- Data on which assertions should have failed

Mutflow provides the zombie detection but with less granular detail than a full matrix-based approach.

---

## Summary of Findings

| Question | Answer | Key Evidence |
|----------|--------|--------------|
| Q1: KMP Support | **No** - JVM only | `kotlin("jvm")` plugin check only; zero KMP-related code |
| Q2: Compiler Plugin | IR transformation via `IrElementTransformerVoid` | `MutflowIrTransformer` visits IR nodes, applies operators |
| Q3: Runtime Registry | Compile-once meta-mutant with global session | `MutationRegistry.check()` discovers points at runtime |
| Q4: Semantic Mutations | **No extension point** | Static IR variants only; no semantic/context-aware support |
| Q5: Parallel Execution | Synchronized lock serializes mutations | `synchronized(lock)` in `MutationRegistry.withSession()` |
| Q6: Zombie Detection | Aggregate verdict only | `MutationResult.Survived` vs `Killed(testName)` |

---

## Files Analyzed

| File Path | Purpose |
|-----------|---------|
| `/tmp/mutflow-repo/DESIGN.md` | Architecture and design documentation |
| `/tmp/mutflow-repo/README.md` | User-facing documentation |
| `mutflow-compiler-plugin/src/main/kotlin/io/github/anschnapp/mutflow/compiler/MutflowIrTransformer.kt` | IR transformer implementation |
| `mutflow-compiler-plugin/src/main/kotlin/io/github/anschnapp/mutflow/compiler/MutationOperator.kt` | Operator interfaces |
| `mutflow-compiler-plugin/src/main/kotlin/io/github/anschnapp/mutflow/compiler/MutflowIrGenerationExtension.kt` | Plugin entry point |
| `mutflow-core/src/main/kotlin/io/github/anschnapp/mutflow/MutationRegistry.kt` | Runtime registry |
| `mutflow-runtime/src/main/kotlin/io/github/anschnapp/mutflow/MutFlow.kt` | Session management |
| `mutflow-runtime/src/main/kotlin/io/github/anschnapp/mutflow/MutFlowSession.kt` | Session implementation |
| `mutflow-gradle-plugin/src/main/kotlin/io/github/anschnapp/mutflow/gradle/MutflowGradlePlugin.kt` | Gradle plugin |
| `mutflow-junit6/src/main/kotlin/io/github/anschnapp/mutflow/junit/MutFlowExtension.kt` | JUnit extension |
| `mutflow-junit6/src/main/kotlin/io/github/anschnapp/mutflow/junit/MutFlowTest.kt` | Annotation |
| `mutflow-annotations/src/main/kotlin/io/github/anschnapp/mutflow/MutationTarget.kt` | Production annotation |
| Various operator implementations (RelationalComparisonOperator.kt, etc.) | Operator logic |
