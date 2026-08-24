/**
 * Custom Gradle task: mutationResults
 *
 * Thin adapter that delegates to the typed MutationResults module in buildSrc.
 *
 * Usage in build.gradle.kts:
 *   apply(plugin = "io.github.anschnapp.mutflow")
 *   apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
 *
 * Output: build/reports/mutation-results.json
 *
 * mutflow's @MutFlowTest JUnit extension runs baseline (run 0) + mutation runs (run 1+)
 * internally. Each mutation run prints MutationTestingSummary to stdout.
 * Gradle captures stdout in JUnit XML's <system-out> elements.
 *
 * The typed data model, pure parsing functions, and JSON serialization live in
 * buildSrc (see .omp/mutation-results-src/). This task only collects JUnit XML
 * files, delegates to the parser, and writes the JSON output.
 */

import io.omp.mutation.MutationResultsParser
import io.omp.mutation.MutationResultsSerializer
import io.omp.mutation.MutationResults
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

        // Delegate to typed module — pure functions, no Gradle dependency
        val mutations = MutationResultsParser.parseMutflowSummary(stdout)
        val sortedTestMethods = testMethods.sorted()
        val results = MutationResultsParser.assembleResults(
            mutations = mutations,
            testMethods = sortedTestMethods,
        )
        val json = MutationResultsSerializer.toJson(results)

        resultsFile.get().asFile.parentFile.mkdirs()
        resultsFile.get().asFile.writeText(json)

        logger.lifecycle("Mutation results written to: ${resultsFile.get().asFile}")
        logger.lifecycle("  Score: ${String.format("%.1f%%", results.mutationScore * 100)} (${results.qualityBand}, ${results.confidence} confidence)")
        logger.lifecycle("  Killed: ${results.killed}, Survived: ${results.survived}, Timed out: ${results.timedOut}")
    }
}
