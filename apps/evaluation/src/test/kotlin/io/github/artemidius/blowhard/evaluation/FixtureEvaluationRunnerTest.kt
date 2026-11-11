package io.github.artemidius.blowhard.evaluation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixtureEvaluationRunnerTest {
    @Test
    fun `versioned dataset contains twelve unique synthetic cases`() {
        val dataset = EvaluationDatasetLoader.load()

        assertEquals("meeting-note-synthetic-v1", dataset.datasetVersion)
        assertEquals(12, dataset.cases.size)
        assertEquals(12, dataset.cases.map { it.id }.distinct().size)
        assertTrue(dataset.cases.all { it.pages.isNotEmpty() })
    }

    @Test
    fun `fixture report is stable complete and honest about unavailable usage`() = runTest {
        val report = FixtureEvaluationRunner.run(EvaluationDatasetLoader.load())

        assertEquals("fixture", report.executionKind)
        assertEquals(12, report.sampleCount)
        assertEquals(12, report.summary.expectedOutcome.passed)
        assertEquals(0, report.summary.expectedOutcome.failed)
        assertEquals(8, report.summary.fieldCorrectness.total)
        assertEquals(12, report.summary.schemaValidity.total)
        assertNull(report.usage)
        assertTrue(report.cases.all { it.latencyMilliseconds == 0L })

        val first = EvaluationReportRenderer.json(report)
        val second = EvaluationReportRenderer.json(
            FixtureEvaluationRunner.run(EvaluationDatasetLoader.load()),
        )
        assertEquals(first, second)
        assertTrue(EvaluationReportRenderer.markdown(report).contains("not a factuality score"))
    }

    @Test
    fun `arguments permit only an explicit bounded output option`() {
        val defaults = EvaluationArguments.parse(emptyArray())
        assertTrue(defaults is EvaluationRunConfiguration.Fixture)
        assertTrue(defaults.output.endsWith("build/reports/evaluation"))
        val custom = EvaluationArguments.parse(arrayOf("--output", "build/custom"))
        assertTrue(custom.output.endsWith("build/custom"))
        assertFailsWith<IllegalArgumentException> { EvaluationArguments.parse(arrayOf("--live")) }
        assertFailsWith<IllegalArgumentException> { EvaluationArguments.parse(arrayOf("--output", "")) }
    }

    @Test
    fun `published machine report matches the deterministic runner`() = runTest {
        val generated = EvaluationReportRenderer.json(
            FixtureEvaluationRunner.run(EvaluationDatasetLoader.load()),
        )
        val published = Files.readString(
            Path.of("../../docs/evaluation/meeting-note-fixture-v1.json").toAbsolutePath().normalize(),
        )

        assertEquals(Json.parseToJsonElement(generated), Json.parseToJsonElement(published))
    }
}
