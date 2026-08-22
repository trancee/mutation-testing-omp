/**
 * Custom Gradle task: mutationResults
 *
 * Runs mutflow mutation tests and captures structured JSON output
 * for the test-auditor agent.
 *
 * Usage in build.gradle.kts:
 *   apply(plugin = "io.github.anschnapp.mutflow")
 *   apply(from = rootProject.file("../.omp/mutation-results.gradle.kts"))
 *
 * Output: build/reports/mutation-results.json
 *
 * mutflow's @MutFlowTest JUnit extension runs baseline (run 0) + mutation runs (run 1+)
 * internally. Each mutation run prints MutationTestingSummary to stdout.
 * Gradle captures stdout in JUnit XML's <system-out> elements.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.util.regex.Pattern

// Configure test to capture stdout in JUnit XML (mutflow prints to stdout)
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    reports {
        junitXml.required.set(true)
    }
}

tasks.register<MutationResultsTask>("mutationResults") {
    group = "verification"
    description = "Runs mutflow mutation tests and outputs structured JSON results"
    dependsOn(tasks.matching { it.name == "test" })
    project.gradle.taskGraph.whenReady {
        val testTask = project.tasks.findByName("test")
        if (testTask is Test) {
            testTask.ignoreFailures = true
        }
    }
}
open class MutationResultsTask : DefaultTask() {

    @get:OutputFile
    val resultsFile: RegularFileProperty = project.objects.fileProperty()
        .convention(project.layout.buildDirectory.file("reports/mutation-results.json"))

    @get:Input
    val testTaskName: Property<String> = project.objects.property(String::class.java)
        .convention("test")

    @TaskAction
    fun generateResults() {
        val buildDir = project.layout.buildDirectory.get().asFile
        val resultsDir = File(buildDir, "test-results/test")

        // Collect all JUnit XML files — they contain <system-out> with mutflow output
        val allStdout = StringBuilder()
        val testMethods = mutableSetOf<String>()

        resultsDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
            .forEach { xmlFile ->
                val content = xmlFile.readText()
                // Extract test method names from <testcase> elements
                val testcasePattern = Pattern.compile("""<testcase[^>]*\bname="([^"]+)"""")
                val tcMatcher = testcasePattern.matcher(content)
                while (tcMatcher.find()) {
                    testMethods.add(tcMatcher.group(1))
                }
                // Extract stdout from <system-out> elements (contains mutflow's MutationTestingSummary)
                val sysoutPattern = Pattern.compile("<system-out>(.*?)</system-out>", Pattern.DOTALL)
                val soMatcher = sysoutPattern.matcher(content)
                while (soMatcher.find()) {
                    allStdout.append(soMatcher.group(1)).append("\n")
                }
            }

        val stdout = allStdout.toString()
        val mutations = parseMutflowSummary(stdout)

        val total = mutations.size
        val killed = mutations.count { it["result"] == "Killed" }
        val survived = mutations.count { it["result"] == "Survived" }
        val timedOut = mutations.count { it["result"] == "TimedOut" }
        val score = if (total > 0) killed.toDouble() / total else 0.0
        val band = when {
            score > 0.8 -> "Excellent"
            score > 0.6 -> "Good"
            score > 0.3 -> "Fair"
            else -> "Poor"
        }
        val confidence = when {
            total < 10 -> "Low"
            total <= 50 -> "Medium"
            else -> "High"
        }

        val sortedTestMethods = testMethods.sorted()
        val mutationsJson = mutations.joinToString(",\n") { m ->
            val killedByTests = m["killedByTests"] as List<String>
            val killedByTestsJson = killedByTests.joinToString(",") { "\"$it\"" }
            val firstKiller = if (killedByTests.isNotEmpty()) "\"${killedByTests.first()}\"" else "null"
            """{"sourceLocation":"${m["sourceLocation"]}","originalOperator":"${m["originalOperator"]}","variantOperator":"${m["variantOperator"]}","result":"${m["result"]}","killedByTest":$firstKiller,"killedByTests":[$killedByTestsJson]}"""
        }
        val testMethodsJson = sortedTestMethods.joinToString(",") { "\"$it\"" }

        // Build per-test-per-mutation matrix: testName -> list of mutation sourceLocations it killed
        val testKillerMatrix = mutableMapOf<String, MutableList<String>>()
        mutations.forEach { m ->
            val killedByTests = m["killedByTests"] as List<String>
            val sourceLocation = m["sourceLocation"].toString()
            killedByTests.forEach { testName ->
                testKillerMatrix.getOrPut(testName) { mutableListOf() }.add(sourceLocation)
            }
        }
        val testKillerMatrixJson = testKillerMatrix.entries.joinToString(",") { (test, locations) ->
            val locsJson = locations.joinToString(",") { "\"$it\"" }
            "\"$test\":[$locsJson]"
        }

        val json = """
        {
            "generatedAt": ${System.currentTimeMillis()},
            "mutationScore": $score,
            "qualityBand": "$band",
            "confidence": "$confidence",
            "totalMutations": $total,
            "killed": $killed,
            "survived": $survived,
            "timedOut": $timedOut,
            "testMethods": [$testMethodsJson],
            "testKillerMatrix": {$testKillerMatrixJson},
            "mutations": [$mutationsJson]
        }
        """.trimIndent()

        resultsFile.get().asFile.parentFile.mkdirs()
        resultsFile.get().asFile.writeText(json)

        logger.lifecycle("Mutation results written to: ${resultsFile.get().asFile}")
        logger.lifecycle("  Score: ${String.format("%.1f%%", score * 100)} ($band, $confidence confidence)")
        logger.lifecycle("  Killed: $killed, Survived: $survived, Timed out: $timedOut")
    }

    /**
     * Parses mutflow's MutationTestingSummary from captured JUnit XML <system-out>.
     *
     * Output format (mutflow summary with multi-killer support):
     *   ✓ (Calculator.kt:7) > → >=
     *       killed by: isPositive returns false for zero()
     *       killed by: testAnotherMethod()
     *   ✗ (Calculator.kt:8) >= → >
     *       SURVIVED - no test caught this mutation!
     *   ⏱ (Calculator.kt:12) + → *
     *       TIMED OUT - likely causes an infinite loop
     *
     * Multiple "killed by:" lines per mutation are captured (full per-test-per-mutation matrix).
     */
    private fun parseMutflowSummary(stdout: String): List<Map<String, Any?>> {
        val mutations = mutableListOf<Map<String, Any?>>()

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
                    "✓" -> "Killed"
                    "✗" -> "Survived"
                    "⏱" -> "TimedOut"
                    else -> "Unknown"
                }

                val killedByTests = mutableListOf<String>()
                if (result == "Killed") {
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
                }

                mutations.add(mapOf(
                    "sourceLocation" to sourceLocation,
                    "originalOperator" to originalOp,
                    "variantOperator" to variantOp,
                    "result" to result,
                    "killedByTests" to killedByTests
                ))
            }
            i++
        }
        return mutations
    }

}
