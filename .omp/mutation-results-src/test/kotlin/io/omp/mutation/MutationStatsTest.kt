package io.omp.mutation

import io.omp.mutation.MutationResultsParser.calculateMetrics
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MutationStatsTest {

    @Test
    fun `all killed yields Excellent band`() {
        val mutations = listOf(
            MutationResult("(Calculator.kt:7)", ">", ">=", MutationResultType.Killed, "testA", listOf("testA")),
            MutationResult("(Calculator.kt:8)", ">=", ">", MutationResultType.Killed, "testB", listOf("testB")),
            MutationResult("(Calculator.kt:12)", "+", "*", MutationResultType.Killed, "testC", listOf("testC")),
        )

        val stats = calculateMetrics(mutations)
        assertEquals(1.0, stats.score)
        assertEquals(QualityBand.Excellent, stats.band)
        assertEquals(ConfidenceLevel.Low, stats.confidence)
        assertEquals(3, stats.total)
        assertEquals(3, stats.killed)
        assertEquals(0, stats.survived)
        assertEquals(0, stats.timedOut)
    }

    @Test
    fun `all survived yields Poor band`() {
        val mutations = listOf(
            MutationResult("(Calculator.kt:7)", ">", ">=", MutationResultType.Survived),
            MutationResult("(Calculator.kt:8)", ">=", ">", MutationResultType.Survived),
            MutationResult("(Calculator.kt:12)", "+", "*", MutationResultType.Survived),
            MutationResult("(Calculator.kt:15)", "-", "+", MutationResultType.Survived),
        )

        val stats = calculateMetrics(mutations)
        assertEquals(0.0, stats.score)
        assertEquals(QualityBand.Poor, stats.band)
        assertEquals(ConfidenceLevel.Low, stats.confidence)
        assertEquals(4, stats.total)
        assertEquals(0, stats.killed)
        assertEquals(4, stats.survived)
        assertEquals(0, stats.timedOut)
    }

    @Test
    fun `medium mutation count yields Medium confidence`() {
        val mutations = (1..15).map {
            MutationResult("(Calculator.kt:$it)", ">", ">=", MutationResultType.Killed, "test", listOf("test"))
        }

        val stats = calculateMetrics(mutations)
        assertEquals(QualityBand.Excellent, stats.band)
        assertEquals(ConfidenceLevel.Medium, stats.confidence)
    }

    @Test
    fun `50+ mutations yields High confidence`() {
        val mutations = (1..60).map {
            MutationResult("(Calculator.kt:$it)", ">", ">=", MutationResultType.Killed, "test", listOf("test"))
        }

        val stats = calculateMetrics(mutations)
        assertEquals(QualityBand.Excellent, stats.band)
        assertEquals(ConfidenceLevel.High, stats.confidence)
    }

    @Test
    fun `empty mutations list yields null score and Poor band`() {
        val stats = calculateMetrics(emptyList())
        assertNull(stats.score)
        assertEquals(QualityBand.Poor, stats.band)
        assertEquals(ConfidenceLevel.Low, stats.confidence)
        assertEquals(0, stats.total)
        assertEquals(0, stats.mutationsEvaluated)
        assertNull(stats.confidenceIntervalLow)
        assertNull(stats.confidenceIntervalHigh)
    }

    @Test
    fun `timed-out counted separately from killed and survived`() {
        val mutations = listOf(
            MutationResult("(Calculator.kt:7)", ">", ">=", MutationResultType.Killed, "testA", listOf("testA")),
            MutationResult("(Calculator.kt:8)", ">=", ">", MutationResultType.Survived),
            MutationResult("(Calculator.kt:12)", "+", "*", MutationResultType.TimedOut),
        )

        val stats = calculateMetrics(mutations)
        assertEquals(1.0 / 3.0, stats.score!!, 0.0001)
        assertEquals(QualityBand.Fair, stats.band)
        assertEquals(1, stats.killed)
        assertEquals(1, stats.survived)
        assertEquals(1, stats.timedOut)
    }

    @Test
    fun `score excludes gaps from denominator`() {
        val mutations = listOf(
            MutationResult("(File.kt:7)", ">", ">=", MutationResultType.Killed, "testA", listOf("testA")),
            MutationResult("(File.kt:8)", ">=", ">", MutationResultType.Survived),
            MutationResult("(File.kt:9)", "+", "*", MutationResultType.Survived),
            MutationResult("(File.kt:10)", "-", "+", MutationResultType.Survived),
        )
        val stats = calculateMetrics(mutations, gaps = 1)
        // 1 killed out of (4 - 1) = 3 evaluated → 1/3
        assertEquals(1.0 / 3.0, stats.score!!, 0.0001)
        assertEquals(4, stats.total)
        assertEquals(3, stats.mutationsEvaluated)
        assertEquals(1, stats.gaps)
    }

    @Test
    fun `score is null when all mutations are gaps`() {
        val mutations = listOf(
            MutationResult("(File.kt:7)", ">", ">=", MutationResultType.Survived),
        )
        val stats = calculateMetrics(mutations, gaps = 1)
        assertNull(stats.score)
        assertEquals(QualityBand.Poor, stats.band)
        assertEquals(0, stats.mutationsEvaluated)
    }
    @Test
    fun `mutationsEvaluated clamped to zero when gaps exceed total`() {
        val mutations = listOf(
            MutationResult("(File.kt:7)", ">", ">=", MutationResultType.Survived),
        )
        val stats = calculateMetrics(mutations, gaps = 5)
        assertNull(stats.score)
        assertEquals(0, stats.mutationsEvaluated)
    }

    @Test
    fun `confidence interval brackets score for all-killed`() {
        val mutations = List(20) {
            MutationResult("($it)", ">", ">=", MutationResultType.Killed, "testA", listOf("testA"))
        }
        val stats = calculateMetrics(mutations)
        assertNotNull(stats.confidenceIntervalLow)
        assertNotNull(stats.confidenceIntervalHigh)
        assertNotNull(stats.score)
        assertTrue(stats.confidenceIntervalLow!! <= stats.score!!)
        assertTrue(stats.confidenceIntervalHigh!! >= stats.score!!)
        assertTrue(stats.confidenceIntervalLow!! >= 0.0)
        assertTrue(stats.confidenceIntervalHigh!! <= 1.0)
    }

    @Test
    fun `confidence interval for 0 kills is centered near zero`() {
        val mutations = List(20) {
            MutationResult("($it)", ">", ">=", MutationResultType.Survived)
        }
        val stats = calculateMetrics(mutations)
        assertNotNull(stats.score)
        assertEquals(0.0, stats.score!!, 0.0001)
        assertNotNull(stats.confidenceIntervalHigh)
        assertTrue(stats.confidenceIntervalHigh!! < 0.2) // Wilson upper bound for 0/20 ≈ 0.19
    }
}
