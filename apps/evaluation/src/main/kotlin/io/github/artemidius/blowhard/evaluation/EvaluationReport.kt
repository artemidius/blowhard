package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.application.EvaluationMetric
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationScore
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class EvaluationReport(
    val schemaVersion: String,
    val executionKind: String,
    val datasetVersion: String,
    val templateVersion: String,
    val modelId: String,
    val configurationVersion: String,
    val reportDate: String,
    val sampleCount: Int,
    val latencyPolicy: String,
    val usage: String?,
    val semanticReview: String,
    val summary: EvaluationSummaryDto,
    val cases: List<EvaluationCaseReport>,
)

@Serializable
internal data class EvaluationCaseReport(
    val id: String,
    val latencyMilliseconds: Long,
    val schemaValidity: String,
    val fieldCorrectness: String,
    val missingFieldHandling: String,
    val sourceResolution: String,
    val expectedOutcome: String,
    val observedRejection: String?,
)

@Serializable
internal data class EvaluationSummaryDto(
    val schemaValidity: MetricDto,
    val fieldCorrectness: MetricDto,
    val missingFieldHandling: MetricDto,
    val sourceResolution: MetricDto,
    val expectedOutcome: MetricDto,
)

@Serializable
internal data class MetricDto(val passed: Int, val failed: Int, val total: Int)

internal object EvaluationReportRenderer {
    private val json = Json { prettyPrint = true }

    fun json(report: EvaluationReport): String = json.encodeToString(report) + "\n"

    fun markdown(report: EvaluationReport): String = buildString {
        appendLine("# Meeting-note ${report.executionKind} evaluation")
        appendLine()
        appendLine("- Dataset: `${report.datasetVersion}` (${report.sampleCount} synthetic cases)")
        appendLine("- Template: `${report.templateVersion}`")
        appendLine("- Model: `${report.modelId}`")
        appendLine("- Execution: ${report.executionKind}; latency policy: ${report.latencyPolicy}")
        appendLine("- Usage: ${report.usage ?: "unavailable"}")
        appendLine("- Date: ${report.reportDate}")
        appendLine()
        appendLine("| Metric | Passed | Failed | Applicable |")
        appendLine("|---|---:|---:|---:|")
        report.summary.rows().forEach { (name, metric) ->
            appendLine("| $name | ${metric.passed} | ${metric.failed} | ${metric.total} |")
        }
        appendLine()
        appendLine("Source resolution verifies literal page evidence only; it is not a factuality score.")
        appendLine(report.semanticReview)
    }
}

internal fun MeetingNoteEvaluationScore.toReport(latencyMilliseconds: Long = 0): EvaluationCaseReport =
    EvaluationCaseReport(
        id = caseId,
        latencyMilliseconds = latencyMilliseconds,
        schemaValidity = schemaValidity.name,
        fieldCorrectness = fieldCorrectness.name,
        missingFieldHandling = missingFieldHandling.name,
        sourceResolution = sourceResolution.name,
        expectedOutcome = expectedOutcome.name,
        observedRejection = observedRejection?.name,
    )

internal fun MeetingNoteEvaluationSummary.toDto(): EvaluationSummaryDto = EvaluationSummaryDto(
    schemaValidity.toDto(),
    fieldCorrectness.toDto(),
    missingFieldHandling.toDto(),
    sourceResolution.toDto(),
    expectedOutcome.toDto(),
)

private fun EvaluationMetric.toDto(): MetricDto = MetricDto(passed, failed, total)

private fun EvaluationSummaryDto.rows(): List<Pair<String, MetricDto>> = listOf(
    "Schema validity" to schemaValidity,
    "Field correctness" to fieldCorrectness,
    "Missing-field handling" to missingFieldHandling,
    "Source resolution" to sourceResolution,
    "Expected outcome" to expectedOutcome,
)
