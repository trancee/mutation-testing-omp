package example

import io.github.anschnapp.mutflow.MutationTarget

/**
 * Simple calculator demonstrating all 5 Scott-CC mutation strategies
 * that map to mutflow's operator catalog:
 *
 * - Boundary conditions: isPositive (> 0), isInRange (>= lower, <= upper)
 * - Boolean logic: isValid (&& short-circuit), hasDiscount (||)
 * - Arithmetic: add (+), multiply (*)
 * - Return values: BooleanReturnOperator (return true/false in Boolean methods)
 * - Exception type mutations are available via `ExceptionTypeSwapOperator`
 * - Exception types: `validateInput` throws `IllegalArgumentException`
 *
 * NOTE: Logging and debug checks are annotated with `@SuppressMutations` or
 * `// mutflow:ignore` to avoid wasting mutation runs on non-business code.
 */
@MutationTarget
class Calculator {

    /** Returns true if x is strictly positive. */
    fun isPositive(x: Int): Boolean = x > 0  // Boundary: > ↔ >=, <

    /** Returns true if x is in [lower, upper] inclusive. */
    fun isInRange(x: Int, lower: Int, upper: Int): Boolean = x >= lower && x <= upper  // Boolean logic + boundary

    /** Simple addition. */
    fun add(a: Int, b: Int): Int = a + b  // Arithmetic: + ↔ -

    /** Simple multiplication. */
    fun multiply(a: Int, b: Int): Int = a * b  // Arithmetic: * ↔ /

    /** Boolean AND with short-circuit. */
    fun isValid(x: Int): Boolean = x > 0 && x < 100  // Boolean logic + boundary

    /** Boolean OR. */
    fun hasDiscount(isMember: Boolean, total: Double): Boolean = isMember || total >= 50.0  // Boolean logic + return value

    /** Returns a greeting string (demonstrates NullableReturnOperator). */
    fun greet(name: String?): String {
        if (name == null) return "Hello, stranger"  // Nullable return
        return "Hello, $name"
    }

    /** Returns true if count is zero (demonstrates VoidFunctionBodyOperator if void, BooleanReturn here). */
    fun isEmpty(count: Int): Boolean = count == 0  // Equality swap: == ↔ !=

    /** Validates that value is non-negative, throwing `IllegalArgumentException` if negative. */
    fun validateInput(value: Int): Int {
        if (value < 0) throw IllegalArgumentException("value must be non-negative, got: $value")
        return value
    }

    // Framework / non-business code — suppressed from mutation testing
    private fun log(message: String) {  // mutflow:ignore logging is not business logic
        println("LOG: $message")
    }

    private fun debugThreshold(): Int = 42  // mutflow:ignore magic number is a heuristic, not business logic
}
