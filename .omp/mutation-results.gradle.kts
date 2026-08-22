/**
 * Custom Gradle task: mutationResults
 *
 * Runs mutflow mutation tests and captures structured JSON output
 * for the test-auditor agent.
 *
 * Usage in build.gradle.kts:
 *   apply(plugin = "io.github.anschnapp.mutflow")
 *   apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
 *
 * Or in settings.gradle.kts:
 *   apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
 *
 * Output: build/reports/mutation-results.json
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern

// Configure test to capture stdout (mutflow prints MutationTestingSummary to console)
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

/**
 * Parses mutflow's console output and JUnit XML to produce structured JSON.
 *
 * mutflow's MutationTestingSummary prints lines like:
 *   ✓ (Calculator.kt:5) > → >=
 *       killed by: testIsPositive
 *   ✗ (Calculator.kt:8) >= → >
 *       SURVIVED - no test caught this mutation!
 *   ⏱ (Calculator.kt:12) + → -
 *       TIMED OUT - likely causes an infinite loop
 */
tasks.register<MutationResultsTask>("mutationResults") {
    group = "verification"
    description = "Runs mutflow mutation tests and outputs structured JSON results"
    dependsOn(tasks.matching { it.name == "test" })
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
        // Capture test stdout by running the test again with output capture
        val capturedOutput = ByteArrayOutputStream()
        val stdoutListener = org.gradle.api.logging.StandardOutputListener { line: String ->
            capturedOutput.write((line + "\n").toByteArray())
        }

        val testTask = project.tasks.findByName(testTaskName.get()) as Test?
            ?: throw org.gradle.api.GradleException("Test task '${testTaskName.get()}' not found")

        // Read JUnit XML for test method names
        val testMethods = parseJUnitXmlResults()

        // The test task already ran (dependsOn). Read its captured stdout.
        // In practice, stdout is captured by Gradle's test logging.
        // We re-read from the test output if available, or parse from a previous run.
        val stdout = readTestTaskStdout(testTask)

        // Parse mutflow's MutationTestingSummary from stdout
        val mutations = parseMutflowSummary(stdout)

        // Compute scores
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

        // Build JSON manually (avoid Kotlin serialization dependency)
        val mutationsJson = mutations.joinToString(",\n") { m ->
            val killedBy = m["killedByTest"]
            val killedByStr = if (killedBy != null) "\"$killedBy\"" else "null"
            """{"sourceLocation":"${m["sourceLocation"]}","originalOperator":"${m["originalOperator"]}","variantOperator":"${m["variantOperator"]}","result":"${m["result"]}","killedByTest":$killedByStr}"""
        }

        val testMethodsJson = testMethods.joinToString(",") { "\"$it\"" }

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
            "mutations": [$mutationsJson]
        }
        """.trimIndent()

        resultsFile.get().asFile.parentFile.mkdirs()
        resultsFile.get().asFile.writeText(json)

        logger.lifecycle("Mutation results written to: ${resultsFile.get().asFile}")
        logger.lifecycle("  Score: ${String.format("%.1f%%", score * 100)} ($band, $confidence confidence)")
        logger.lifecycle("  Killed: $killed, Survived: $survived, Timed out: $timedOut")
    }

    private fun readTestTaskStdout(testTask: Test): String {
        // Gradle captures test stdout in the test task's output
        // Try reading from the test results directory
        val buildDir = project.layout.buildDirectory.get().asFile
        val stdoutFile = File(buildDir, "reports/tests/${testTask.name}/test-stdout.txt")
        val altFile = File(buildDir, "test-results/${testTask.name}/test-stdout.txt")
        val legacyFile = File(buildDir, "test-results/test/test-stdout.txt")

        for (f in listOf(stdoutFile, altFile, legacyFile)) {
            if (f.exists()) {
                return f.readText()
            }
        }

        // If no stdout file found, the test task may have printed to console
        // Fall back to scanning the test task's output directory
        val testDir = File(buildDir, "test-results/test")
        if (testDir.exists()) {
            val combined = StringBuilder()
            testDir.walkTopDown().forEach { f ->
                if (f.isFile && (f.name.endsWith(".txt") || f.name.endsWith(".log"))) {
                    combined.append(f.readText()).append("\n")
                }
            }
            return combined.toString()
        }

        return ""
    }

    private fun parseJUnitXmlResults(): List<String> {
        val testMethods = mutableListOf<String>()
        val buildDir = project.layout.buildDirectory.get().asFile
        val resultsDir = File(buildDir, "test-results/test")
        if (!resultsDir.exists()) return testMethods

        resultsDir.walkTopDown()
            .filter { f -> f.isFile && f.name.startsWith("TEST-") && f.extension == "xml" }
            .forEach { xmlFile ->
                val content = xmlFile.readText()
                val pattern = Pattern.compile("""<testcase[^>]*name="([^"]+)"""")
                val matcher = pattern.matcher(content as CharSequence)
                while (matcher.find()) {
                    testMethods.add(matcher.group(1))
                }
            }

        return testMethods.distinct().sorted()
    }

    private fun parseMutflowSummary(stdout: String): List<Map<String, Any?>> {
        val mutations = mutableListOf<Map<String, Any?>>()

        val mutationPattern = Pattern.compile(
            """([✓✗⏱])\s+\(([^)]+)\)\s+(.+?)\s*(?:→|->)\s*(.+)"""
        )
        val killedByPattern = Pattern.compile("""(?:killed by:?\s*(.+))""")

        val lines = stdout.lines().filter { it.isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
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

                var killedByTest: String? = null
                if (result == "Killed" && i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val killedMatcher = killedByPattern.matcher(nextLine)
                    if (killedMatcher.matches()) {
                        killedByTest = killedMatcher.group(1).trim()
                        i++
                    }
                }

                mutations.add(mapOf(
                    "sourceLocation" to sourceLocation,
                    "originalOperator" to originalOp,
                    "variantOperator" to variantOp,
                    "result" to result,
                    "killedByTest" to killedByTest
                ))
            }
            i++
        }

        return mutations
    }
}
