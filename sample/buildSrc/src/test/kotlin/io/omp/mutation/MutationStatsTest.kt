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
    fun `empty mutations list yields zero score and Poor band`() {
        val stats = calculateMetrics(emptyList())
        assertEquals(0.0, stats.score)
        assertEquals(QualityBand.Poor, stats.band)
        assertEquals(ConfidenceLevel.Low, stats.confidence)
        assertEquals(0, stats.total)
    }

    @Test
    fun `timed-out counted separately from killed and survived`() {
        val mutations = listOf(
            MutationResult("(Calculator.kt:7)", ">", ">=", MutationResultType.Killed, "testA", listOf("testA")),
            MutationResult("(Calculator.kt:8)", ">=", ">", MutationResultType.Survived),
            MutationResult("(Calculator.kt:12)", "+", "*", MutationResultType.TimedOut),
        )

        val stats = calculateMetrics(mutations)
        assertEquals(1.0 / 3.0, stats.score, 0.0001)
        assertEquals(QualityBand.Fair, stats.band)
        assertEquals(1, stats.killed)
        assertEquals(1, stats.survived)
        assertEquals(1, stats.timedOut)
    }
}
