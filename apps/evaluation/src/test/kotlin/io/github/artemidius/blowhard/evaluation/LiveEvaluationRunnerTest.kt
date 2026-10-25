package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiveEvaluationRunnerTest {
    @Test
    fun `live runner honors the explicit case ceiling and records honest metadata`() = runTest {
        val dataset = EvaluationDatasetLoader.load()
        val outputs = dataset.cases.associate { "eval-${it.id}" to it.providerJson }

        val report = LiveEvaluationRunner.run(
            dataset = dataset,
            maximumCases = 2,
            modelId = "requested-model",
            generator = { request ->
                val output = outputs.entries.single { request.sourceJson.contains(it.key) }.value
                MeetingNoteGenerationResult.Generated(output, "reported-model")
            },
            reportDate = "2026-11-11",
        )

        assertEquals("live", report.executionKind)
        assertEquals("requested-model", report.modelId)
        assertEquals(2, report.sampleCount)
        assertEquals(2, report.summary.expectedOutcome.passed)
        assertEquals("unavailable from current structured-output adapter", report.usage)
        assertTrue(report.cases.all { it.latencyMilliseconds >= 0 })
    }

    @Test
    fun `live arguments reject excessive cases and unsafe model identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            EvaluationArguments.parse(
                arrayOf("--live", "--model", "model", "--max-cases", "7", "--output", "build/live"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EvaluationArguments.parse(
                arrayOf("--live", "--model", "bad model", "--max-cases", "1", "--output", "build/live"),
            )
        }
        val live = EvaluationArguments.parse(
            arrayOf("--live", "--model", "model-v1", "--max-cases", "2", "--output", "build/live"),
        )
        assertTrue(live is EvaluationRunConfiguration.Live)
        assertEquals(2, live.maximumCases)
    }
}
