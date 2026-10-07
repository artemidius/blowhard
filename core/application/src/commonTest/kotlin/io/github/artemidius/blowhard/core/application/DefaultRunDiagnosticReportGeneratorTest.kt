package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunDiagnosticReportResult
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunInspector
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.api.RunTerminalStatus
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.TokenUsage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultRunDiagnosticReportGeneratorTest {
    @Test
    fun `live failure report is deterministic and preserves unknown counts`() = runTest {
        val inspection = RunInspection(
            runId = RunId("run-42"),
            conversationId = ConversationId("conversation-7"),
            target = GenerationTarget.Live("mistral-small-latest"),
            parameters = GenerationParameters(maxOutputTokens = 1_024, temperature = 0.2),
            usage = TokenUsage(inputTokens = 17, totalTokens = 29),
            events = listOf(
                RunInspectionEvent.Accepted(RunTimestamp(1_000)),
                RunInspectionEvent.Started(RunTimestamp(1_010)),
                RunInspectionEvent.Finished(
                    at = RunTimestamp(1_100),
                    status = RunTerminalStatus.FAILED,
                    failureKind = RunFailureKind.RATE_LIMITED,
                    retryAdvice = RunRetryAdvice.RETRY_AFTER_DELAY,
                    retryAfterSeconds = 8,
                ),
            ),
        )

        val result = generator(inspection).generate(inspection.runId)
        val report = assertIs<RunDiagnosticReportResult.Generated>(result).report

        assertEquals("blowhard-run-diagnostic.json", report.suggestedFileName)
        assertEquals(EXPECTED_LIVE_REPORT, report.content)
        assertTrue(report.content.contains("\"outputTokens\": null"))
    }

    @Test
    fun `demo report contains no conversation content or provider reference`() = runTest {
        val sensitiveValues = listOf(
            "sensitive prompt",
            "sensitive generated output",
            "secret-api-key",
            "sensitive-provider-reference",
        )
        val inspection = RunInspection(
            runId = RunId("demo-run"),
            conversationId = ConversationId("demo-conversation"),
            target = GenerationTarget.Demo,
            parameters = GenerationParameters(),
            events = listOf(
                RunInspectionEvent.Accepted(RunTimestamp(2_000)),
                RunInspectionEvent.Finished(RunTimestamp(2_100), RunTerminalStatus.SUCCEEDED),
            ),
        )

        val report = assertIs<RunDiagnosticReportResult.Generated>(
            generator(inspection).generate(inspection.runId),
        ).report

        assertTrue(report.content.contains("\"mode\": \"demo\""))
        assertTrue(report.content.contains("\"usage\": null"))
        sensitiveValues.forEach { sensitive -> assertFalse(report.content.contains(sensitive)) }
    }

    @Test
    fun `missing and oversized inspections fail with typed outcomes`() = runTest {
        val missingGenerator = DefaultRunDiagnosticReportGenerator(RunInspector { RunInspectionResult.NotFound })
        assertEquals(RunDiagnosticReportResult.NotFound, missingGenerator.generate(RunId("missing")))

        val oversized = RunInspection(
            runId = RunId("x".repeat(70_000)),
            conversationId = ConversationId("conversation"),
            target = GenerationTarget.Demo,
            parameters = GenerationParameters(),
            events = listOf(RunInspectionEvent.Accepted(RunTimestamp(1))),
        )
        assertEquals(
            RunDiagnosticReportResult.Unavailable,
            generator(oversized).generate(oversized.runId),
        )
    }

    private fun generator(inspection: RunInspection): DefaultRunDiagnosticReportGenerator =
        DefaultRunDiagnosticReportGenerator(RunInspector { RunInspectionResult.Found(inspection) })
}

private val EXPECTED_LIVE_REPORT = """
    {
      "schemaVersion": 1,
      "application": "Blowhard",
      "run": {
        "id": "run-42",
        "conversationId": "conversation-7",
        "target": {
          "mode": "live",
          "modelId": "mistral-small-latest"
        },
        "parameters": {
          "maxOutputTokens": 1024,
          "temperature": 0.2
        },
        "usage": {
          "inputTokens": 17,
          "outputTokens": null,
          "totalTokens": 29
        },
        "events": [
          {
            "type": "accepted",
            "atEpochMilliseconds": 1000
          },
          {
            "type": "started",
            "atEpochMilliseconds": 1010
          },
          {
            "type": "finished",
            "status": "failed",
            "failureKind": "rate_limited",
            "retryAdvice": "retry_after_delay",
            "retryAfterSeconds": 8,
            "atEpochMilliseconds": 1100
          }
        ]
      }
    }
""".trimIndent() + "\n"
