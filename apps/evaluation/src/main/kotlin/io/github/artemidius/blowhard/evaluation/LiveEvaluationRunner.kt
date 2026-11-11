package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.application.DefaultMeetingNotes
import io.github.artemidius.blowhard.core.application.MeetingNoteClock
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationScorer
import io.github.artemidius.blowhard.core.application.MeetingNoteIdFactory
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import java.time.LocalDate
import kotlin.time.TimeSource

internal object LiveEvaluationRunner {
    suspend fun run(
        dataset: EvaluationDataset,
        maximumCases: Int,
        modelId: String,
        generator: MeetingNoteGenerationPort,
        reportDate: String = LocalDate.now().toString(),
    ): EvaluationReport {
        require(maximumCases in 1..dataset.cases.size) { "Live evaluation case limit is invalid" }
        val observations = dataset.cases.take(maximumCases).map { fixtureCase ->
            evaluate(fixtureCase, generator)
        }
        val scores = observations.map { observed ->
            MeetingNoteEvaluationScorer.score(observed.case.toCoreCase(), observed.outcome)
        }
        return EvaluationReport(
            schemaVersion = "meeting-note-evaluation-report-v1",
            executionKind = "live",
            datasetVersion = dataset.datasetVersion,
            templateVersion = dataset.templateVersion,
            modelId = modelId,
            configurationVersion = "bounded-live-evaluation-v1",
            reportDate = reportDate,
            sampleCount = scores.size,
            latencyPolicy = "wall-clock-per-case",
            usage = "unavailable from current structured-output adapter",
            semanticReview = "not reviewed; machine scores are not a factuality judgment",
            summary = MeetingNoteEvaluationScorer.summarize(scores).toDto(),
            cases = scores.zip(observations) { score, observed -> score.toReport(observed.latencyMilliseconds) },
        )
    }

    private suspend fun evaluate(
        fixtureCase: EvaluationDatasetCase,
        generator: MeetingNoteGenerationPort,
    ): LiveObservation {
        val coreCase = fixtureCase.toCoreCase()
        val service = DefaultMeetingNotes(
            documentStore = EvaluationDocumentStore(coreCase.extraction),
            generator = generator,
            noteStore = EvaluationNoteStore(),
            idFactory = MeetingNoteIdFactory { MeetingNoteId("live-${fixtureCase.id}") },
            clock = MeetingNoteClock { System.currentTimeMillis() },
        )
        val mark = TimeSource.Monotonic.markNow()
        val outcome = service.generate(coreCase.extraction.sourceDocumentId)
        return LiveObservation(fixtureCase, outcome, mark.elapsedNow().inWholeMilliseconds)
    }
}

private data class LiveObservation(
    val case: EvaluationDatasetCase,
    val outcome: MeetingNoteGenerationOutcome,
    val latencyMilliseconds: Long,
)
