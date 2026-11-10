package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.core.api.RunDiagnosticReportGenerator
import io.github.artemidius.blowhard.core.api.RunDiagnosticReportResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunInspector
import io.github.artemidius.blowhard.core.api.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Generates deterministic JSON from the content-free [RunInspector] contract. */
public class DefaultRunDiagnosticReportGenerator(private val inspector: RunInspector) : RunDiagnosticReportGenerator {
    override suspend fun generate(runId: RunId): RunDiagnosticReportResult =
        when (val result = inspector.inspect(runId)) {
            is RunInspectionResult.Found -> result.inspection.toReport()
            RunInspectionResult.NotFound -> RunDiagnosticReportResult.NotFound
        }
}

private fun RunInspection.toReport(): RunDiagnosticReportResult = try {
    val content = REPORT_JSON.encodeToString(JsonElement.serializer(), reportJson()) + "\n"
    RunDiagnosticReportResult.Generated(RunDiagnosticReport(RunDiagnosticReport.FILE_NAME, content))
} catch (_: IllegalArgumentException) {
    RunDiagnosticReportResult.Unavailable
}

private fun RunInspection.reportJson(): JsonObject = buildJsonObject {
    put("schemaVersion", REPORT_SCHEMA_VERSION)
    put("application", "Blowhard")
    put(
        "run",
        buildJsonObject {
            put("id", runId.value)
            put("conversationId", conversationId.value)
            put("target", targetJson(target))
            put("parameters", parametersJson(this@reportJson))
            put("usage", usageJson(usage))
            put("events", JsonArray(events.map(::eventJson)))
        },
    )
}

private fun targetJson(target: GenerationTarget): JsonObject = buildJsonObject {
    when (target) {
        GenerationTarget.Demo -> put("mode", "demo")

        is GenerationTarget.Live -> {
            put("mode", "live")
            put("modelId", target.modelId)
        }
    }
}

private fun parametersJson(inspection: RunInspection): JsonObject = buildJsonObject {
    putNullable("maxOutputTokens", inspection.parameters.maxOutputTokens)
    putNullable("temperature", inspection.parameters.temperature)
}

private fun usageJson(usage: TokenUsage?): JsonElement = usage?.let {
    buildJsonObject {
        putNullable("inputTokens", it.inputTokens)
        putNullable("outputTokens", it.outputTokens)
        putNullable("totalTokens", it.totalTokens)
    }
} ?: JsonNull

private fun eventJson(event: RunInspectionEvent): JsonObject = buildJsonObject {
    when (event) {
        is RunInspectionEvent.Accepted -> put("type", "accepted")

        is RunInspectionEvent.Started -> put("type", "started")

        is RunInspectionEvent.Finished -> {
            put("type", "finished")
            put("status", event.status.name.lowercase())
            putNullable("failureKind", event.failureKind?.name?.lowercase())
            putNullable("retryAdvice", event.retryAdvice?.name?.lowercase())
            putNullable("retryAfterSeconds", event.retryAfterSeconds)
        }
    }
    put("atEpochMilliseconds", event.at.epochMilliseconds)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Number?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

private const val REPORT_SCHEMA_VERSION = 1
private val REPORT_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    explicitNulls = true
}
