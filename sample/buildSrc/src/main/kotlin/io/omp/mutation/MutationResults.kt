package io.omp.mutation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Quality band thresholds, matching the original mutation-results.gradle.kts logic:
 *   Excellent: >80%, Good: >60%, Fair: >30%, Poor: ≤30%
 */
enum class QualityBand {
    @SerialName("Excellent") Excellent,
    @SerialName("Good") Good,
    @SerialName("Fair") Fair,
    @SerialName("Poor") Poor,
}

/**
 * Confidence level based on mutation count:
 *   Low: <10, Medium: 10–50, High: 50+
 */
enum class ConfidenceLevel {
    @SerialName("Low") Low,
    @SerialName("Medium") Medium,
    @SerialName("High") High,
}

/**
 * Intermediate metrics computed from a list of mutation results.
 * Not serialized directly — its fields are spread into [MutationResults].
 */
data class MutationStats(
    val score: Double,
    val band: QualityBand,
    val confidence: ConfidenceLevel,
    val total: Int,
    val killed: Int,
    val survived: Int,
    val timedOut: Int,
)

/**
 * The complete mutation testing results output, serialized to
 * `mutation-results.json` for consumption by the test-auditor agent.
 *
 * Field names and order match the existing string-template JSON exactly
 * for backward compatibility.
 */
@Serializable
data class MutationResults(
    @SerialName("generatedAt") val generatedAt: Long,
    @SerialName("mutationScore") val mutationScore: Double,
    @SerialName("qualityBand") val qualityBand: QualityBand,
    @SerialName("confidence") val confidence: ConfidenceLevel,
    @SerialName("totalMutations") val totalMutations: Int,
    @SerialName("killed") val killed: Int,
    @SerialName("survived") val survived: Int,
    @SerialName("timedOut") val timedOut: Int,
    @SerialName("testMethods") val testMethods: List<String>,
    @SerialName("testKillerMatrix") val testKillerMatrix: Map<String, List<String>>,
    @SerialName("mutations") val mutations: List<MutationResult>,
)
