package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.application.DefaultMeetingNotes
import io.github.artemidius.blowhard.core.application.MeetingNoteClock
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationScorer
import io.github.artemidius.blowhard.core.application.MeetingNoteIdFactory
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult

internal object FixtureEvaluationRunner {
    suspend fun run(dataset: EvaluationDataset): EvaluationReport {
        val scores = dataset.cases.map { fixtureCase ->
            val coreCase = fixtureCase.toCoreCase()
            val outcome = evaluate(fixtureCase, coreCase)
            MeetingNoteEvaluationScorer.score(coreCase, outcome)
        }
        val summary = MeetingNoteEvaluationScorer.summarize(scores)
        return EvaluationReport(
            schemaVersion = "meeting-note-evaluation-report-v1",
            executionKind = "fixture",
            datasetVersion = dataset.datasetVersion,
            templateVersion = dataset.templateVersion,
            modelId = dataset.fixtureModel,
            configurationVersion = "fixture-evaluation-v1",
            reportDate = dataset.reportDate,
            sampleCount = scores.size,
            latencyPolicy = "deterministic-zero",
            usage = null,
            semanticReview = "synthetic expectations hand-reviewed; live model quality not measured",
            summary = summary.toDto(),
            cases = scores.map { it.toReport() },
        )
    }

    private suspend fun evaluate(
        fixtureCase: EvaluationDatasetCase,
        coreCase: io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationCase,
    ): MeetingNoteGenerationOutcome {
        val service = DefaultMeetingNotes(
            documentStore = EvaluationDocumentStore(coreCase.extraction),
            generator = { MeetingNoteGenerationResult.Generated(fixtureCase.providerJson, FIXTURE_MODEL_ID) },
            noteStore = EvaluationNoteStore(),
            idFactory = MeetingNoteIdFactory { MeetingNoteId("note-${fixtureCase.id}") },
            clock = MeetingNoteClock { 0 },
        )
        return service.generate(coreCase.extraction.sourceDocumentId)
    }
}

private const val FIXTURE_MODEL_ID = "fixture-meeting-note-v1"
