package example

import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for [Calculator] — annotated with @MutFlowTest so mutflow's
 * JUnit 6 extension runs baseline (run 0) + one mutation per run (run 1+).
 *
 * Each test wraps business logic calls in MutFlow.underTest { } so
 * mutflow can activate mutations during mutation runs.
 */
@MutFlowTest
class CalculatorTest {

    private val calc = Calculator()

    @Test
    fun testIsPositive() {
        assertTrue(MutFlow.underTest { calc.isPositive(5) })
        assertFalse(MutFlow.underTest { calc.isPositive(-1) })
        // Boundary: 0 is NOT positive
        assertFalse(MutFlow.underTest { calc.isPositive(0) })
        // Boundary: x=1 distinguishes > 0 from > 1. The isPositive(0=false)
        // assertion above kills the > -> >= relational mutant, but the
        // 0 -> 1 constant-boundary mutant (x > 1) is invisible at x=0 (both
        // false), so x=1 is required: 1>0=true (orig) vs 1>1=false (mutant).
        assertTrue(MutFlow.underTest { calc.isPositive(1) })
    }

    @Test
    fun testIsInRange() {
        assertTrue(MutFlow.underTest { calc.isInRange(5, 1, 10) })
        assertFalse(MutFlow.underTest { calc.isInRange(0, 1, 10) })
        assertFalse(MutFlow.underTest { calc.isInRange(11, 1, 10) })
        // Boundary: exact bounds
        assertTrue(MutFlow.underTest { calc.isInRange(1, 1, 10) })
        assertTrue(MutFlow.underTest { calc.isInRange(10, 1, 10) })
    }

    @Test
    fun testAdd() {
        assertEquals(7, MutFlow.underTest { calc.add(3, 4) })
        assertEquals(0, MutFlow.underTest { calc.add(-5, 5) })
        assertEquals(-8, MutFlow.underTest { calc.add(-3, -5) })
    }

    @Test
    fun testMultiply() {
        assertEquals(12, MutFlow.underTest { calc.multiply(3, 4) })
        assertEquals(0, MutFlow.underTest { calc.multiply(0, 5) })
        assertEquals(-15, MutFlow.underTest { calc.multiply(-3, 5) })
    }

    @Test
    fun testIsValid() {
        assertTrue(MutFlow.underTest { calc.isValid(50) })
        assertFalse(MutFlow.underTest { calc.isValid(-1) })
        assertFalse(MutFlow.underTest { calc.isValid(150) })
        // Boundary: lower edge. isValid(0) is false under the original (0 > 0)
        // and under the 0 -> -1 mutant (0 > -1 = true), so assertFalse(0)
        // kills BOTH the > -> >= relational mutant (0 >= 0 = true) and 0 -> -1.
        assertFalse(MutFlow.underTest { calc.isValid(0) })
        // isValid(1) is true under the original (1 > 0) but the 0 -> 1 mutant
        // makes 1 > 1 = false, so assertTrue(1) kills that constant-boundary mutant.
        assertTrue(MutFlow.underTest { calc.isValid(1) })
        // Boundary: upper edge. isValid(99) is true originally (99 < 100); the
        // 100 -> 99 mutant makes 99 < 99 = false, killing it.
        assertTrue(MutFlow.underTest { calc.isValid(99) })
        // isValid(100) is false originally (100 < 100 = false); the 100 -> 101
        // mutant (100 < 101 = true) and the < -> <= mutant (100 <= 100 = true)
        // both flip to true, so assertFalse(100) kills both.
        assertFalse(MutFlow.underTest { calc.isValid(100) })
    }

    @Test
    fun testHasDiscount() {
        assertTrue(MutFlow.underTest { calc.hasDiscount(isMember = true, total = 0.0) })
        assertFalse(MutFlow.underTest { calc.hasDiscount(isMember = false, total = 10.0) })
        assertTrue(MutFlow.underTest { calc.hasDiscount(isMember = false, total = 50.0) })
        // Boundary: just below the 50.0 threshold. Original: 49.0 >= 50.0 =
        // false (assertFalse passes); the 50.0 -> 49.0 mutant: 49.0 >= 49.0 =
        // true, which makes assertFalse fail -> kills the mutant.
        assertFalse(MutFlow.underTest { calc.hasDiscount(isMember = false, total = 49.0) })
    }

    @Test
    fun testGreet() {
        assertEquals("Hello, World", MutFlow.underTest { calc.greet("World") })
        assertEquals("Hello, stranger", MutFlow.underTest { calc.greet(null) })
    }

    @Test
    fun testIsEmpty() {
        assertTrue(MutFlow.underTest { calc.isEmpty(0) })
        assertFalse(MutFlow.underTest { calc.isEmpty(5) })
    }
}
