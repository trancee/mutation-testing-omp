package io.omp.mutation

import java.util.regex.Pattern

/**
 * Pure parsing and computation functions for mutflow's MutationTestingSummary output.
 *
 * These functions have no Gradle dependency — they can be unit-tested directly.
 * The Gradle [MutationResultsTask] is a thin adapter that calls these.
 */
object MutationResultsParser {

    /**
     * Parses mutflow's MutationTestingSummary from captured JUnit XML `<system-out>`.
     *
     * mutflow output format (with multi-killer support):
     * ```
     * ✓ (Calculator.kt:7) > → >=
     *     killed by: isPositive returns false for zero()
     *     killed by: testAnotherMethod()
     * ✗ (Calculator.kt:8) >= → >
     *     SURVIVED - no test caught this mutation!
     * ⏱ (Calculator.kt:12) + → *
     *     TIMED OUT - likely causes an infinite loop
     * ```
     *
     * Multiple "killed by:" lines per mutation are captured (full per-test-per-mutation matrix).
     */
    fun parseMutflowSummary(stdout: String): List<MutationResult> {
        val mutations = mutableListOf<MutationResult>()

        val mutationPattern = Pattern.compile(
            """([✓✗⏱])\s*\(([^)]+)\)\s+(.+?)\s*(?:→|->)\s*(.+)"""
        )
        val killedByPattern = Pattern.compile("""(?:killed by:?\s*(.+))""")

        val lines = stdout.lines().filter { it.isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i].replace("║", "").trim()
            val matcher = mutationPattern.matcher(line)
            if (matcher.matches()) {
                val statusIcon = matcher.group(1)
                val sourceLocation = matcher.group(2)
                val originalOp = matcher.group(3).trim()
                val variantOp = matcher.group(4).trim()

                val result = when (statusIcon) {
                    "✓" -> MutationResultType.Killed
                    "✗" -> MutationResultType.Survived
                    "⏱" -> MutationResultType.TimedOut
                    else -> continue // skip unrecognized status icons
                }

                val killedByTests = mutableListOf<String>()
                val killedByTest: String? = if (result == MutationResultType.Killed) {
                    // Collect ALL "killed by:" lines that follow (full killer set)
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].replace("║", "").trim()
                        val killedMatcher = killedByPattern.matcher(nextLine)
                        if (killedMatcher.matches()) {
                            killedByTests.add(killedMatcher.group(1).trim())
                            j++ // consume the killed-by line
                        } else {
                            break // stop at first non-killed-by line
                        }
                    }
                    i = j - 1 // advance past all consumed killed-by lines
                    killedByTests.firstOrNull()
                } else {
                    null
                }

                mutations.add(MutationResult(
                    sourceLocation = sourceLocation,
                    originalOperator = originalOp,
                    variantOperator = variantOp,
                    result = result,
                    killedByTest = killedByTest,
                    killedByTests = killedByTests,
                ))
            }
            i++
        }
        return mutations
    }

    /**
     * Calculates mutation score, quality band, confidence level, and totals
     * from a list of parsed mutation results.
     */
    fun calculateMetrics(mutations: List<MutationResult>): MutationStats {
        val total = mutations.size
        val killed = mutations.count { it.result == MutationResultType.Killed }
        val survived = mutations.count { it.result == MutationResultType.Survived }
        val timedOut = mutations.count { it.result == MutationResultType.TimedOut }
        val score = if (total > 0) killed.toDouble() / total else 0.0

        val band = when {
            score > 0.8 -> QualityBand.Excellent
            score > 0.6 -> QualityBand.Good
            score > 0.3 -> QualityBand.Fair
            else -> QualityBand.Poor
        }

        val confidence = when {
            total < 10 -> ConfidenceLevel.Low
            total <= 50 -> ConfidenceLevel.Medium
            else -> ConfidenceLevel.High
        }

        return MutationStats(
            score = score,
            band = band,
            confidence = confidence,
            total = total,
            killed = killed,
            survived = survived,
            timedOut = timedOut,
        )
    }

    /**
     * Builds the per-test-per-mutation killer matrix: test name → list of
     * mutation source locations it killed.
     */
    fun buildTestKillerMatrix(mutations: List<MutationResult>): Map<String, List<String>> {
        val testKillerMatrix = mutableMapOf<String, MutableList<String>>()
        mutations.forEach { m ->
            m.killedByTests.forEach { testName ->
                testKillerMatrix.getOrPut(testName) { mutableListOf() }.add(m.sourceLocation)
            }
        }
        return testKillerMatrix
    }

    /**
     * Assembles a complete [MutationResults] from parsed mutations, test method
     * names, and the generated-at timestamp.
     */
    fun assembleResults(
        mutations: List<MutationResult>,
        testMethods: List<String>,
        generatedAt: Long = System.currentTimeMillis(),
    ): MutationResults {
        val stats = calculateMetrics(mutations)
        val testKillerMatrix = buildTestKillerMatrix(mutations)

        return MutationResults(
            generatedAt = generatedAt,
            mutationScore = stats.score,
            qualityBand = stats.band,
            confidence = stats.confidence,
            totalMutations = stats.total,
            killed = stats.killed,
            survived = stats.survived,
            timedOut = stats.timedOut,
            testMethods = testMethods,
            testKillerMatrix = testKillerMatrix,
            mutations = mutations,
        )
    }
}
