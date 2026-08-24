package io.omp.mutation

import io.omp.mutation.MutationResultsParser.parseMutflowSummary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MutationResultsParserTest {

    @Test
    fun `multi-killer mutation captures all killing tests`() {
        val stdout = """
            ✓ (Calculator.kt:7) > → >=
                killed by: testIsPositive
                killed by: testIsPositiveBoundary
                killed by: testPositiveNumbers
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(1, mutations.size)

        val mutation = mutations[0]
        assertEquals(MutationResultType.Killed, mutation.result)
        assertEquals("Calculator.kt:7", mutation.sourceLocation)
        assertEquals(">", mutation.originalOperator)
        assertEquals(">=", mutation.variantOperator)
        assertEquals(3, mutation.killedByTests.size)
        assertEquals("testIsPositive", mutation.killedByTest)
        assertTrue(mutation.killedByTests.contains("testIsPositive"))
        assertTrue(mutation.killedByTests.contains("testIsPositiveBoundary"))
        assertTrue(mutation.killedByTests.contains("testPositiveNumbers"))
    }

    @Test
    fun `survived mutation has no killer`() {
        val stdout = """
            ✗ (Calculator.kt:8) >= → >
                SURVIVED - no test caught this mutation!
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(1, mutations.size)

        val mutation = mutations[0]
        assertEquals(MutationResultType.Survived, mutation.result)
        assertNull(mutation.killedByTest)
        assertTrue(mutation.killedByTests.isEmpty())
    }

    @Test
    fun `timed-out mutation has no killer`() {
        val stdout = """
            ⏱ (Calculator.kt:12) + → *
                TIMED OUT - likely causes an infinite loop
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(1, mutations.size)

        val mutation = mutations[0]
        assertEquals(MutationResultType.TimedOut, mutation.result)
        assertNull(mutation.killedByTest)
        assertTrue(mutation.killedByTests.isEmpty())
    }

    @Test
    fun `empty stdout yields no mutations`() {
        val mutations = parseMutflowSummary("")
        assertTrue(mutations.isEmpty())
    }

    @Test
    fun `mixed results parse correctly`() {
        val stdout = """
            ✓ (Calculator.kt:7) > → >=
                killed by: testIsPositive
                killed by: testIsPositiveBoundary
            ✗ (Calculator.kt:8) >= → >
                SURVIVED - no test caught this mutation!
            ⏱ (Calculator.kt:12) + → *
                TIMED OUT - likely causes an infinite loop
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(3, mutations.size)
        assertEquals(MutationResultType.Killed, mutations[0].result)
        assertEquals(MutationResultType.Survived, mutations[1].result)
        assertEquals(MutationResultType.TimedOut, mutations[2].result)
        assertEquals(2, mutations[0].killedByTests.size)
    }

    @Test
    fun `malformed lines are skipped`() {
        val stdout = """
            Some random log line
            ✓ (Calculator.kt:7) > → >=
                killed by: testIsPositive
            Another random line
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(1, mutations.size)
        assertEquals(MutationResultType.Killed, mutations[0].result)
    }

    @Test
    fun `unicode box-drawing character is stripped from killed-by lines`() {
        val stdout = """
            ✓ (Calculator.kt:7) > → >=
                ║ killed by: testIsPositive
        """.trimIndent()

        val mutations = parseMutflowSummary(stdout)
        assertEquals(1, mutations.size)
        assertEquals(1, mutations[0].killedByTests.size)
        assertEquals("testIsPositive", mutations[0].killedByTests[0])
    }
}
