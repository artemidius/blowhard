package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeetingNoteEvaluationScorerTest {
    @Test
    fun `known positive passes every independent dimension`() {
        val case = acceptedCase("positive")

        val score = MeetingNoteEvaluationScorer.score(case, generated(case.expectation.content!!))

        assertEquals(EvaluationCheck.PASS, score.schemaValidity)
        assertEquals(EvaluationCheck.PASS, score.fieldCorrectness)
        assertEquals(EvaluationCheck.PASS, score.missingFieldHandling)
        assertEquals(EvaluationCheck.PASS, score.sourceResolution)
        assertEquals(EvaluationCheck.PASS, score.expectedOutcome)
    }

    @Test
    fun `unsupported citation fails resolution separately from field correctness`() {
        val case = acceptedCase("citation")
        val unsupported = case.expectation.content!!.copy(
            decisions = listOf(MeetingDecision("Ship", listOf(MeetingNoteSourceReference(0, "not present")))),
        )

        val score = MeetingNoteEvaluationScorer.score(case, generated(unsupported))

        assertEquals(EvaluationCheck.FAIL, score.fieldCorrectness)
        assertEquals(EvaluationCheck.PASS, score.missingFieldHandling)
        assertEquals(EvaluationCheck.FAIL, score.sourceResolution)
    }

    @Test
    fun `invented missing values fail missing-field handling`() {
        val case = acceptedCase("abstention")
        val invented = case.expectation.content!!.copy(
            actionItems = listOf(
                MeetingActionItem(
                    description = "Review",
                    owner = "Invented owner",
                    dueDate = null,
                    sources = listOf(MeetingNoteSourceReference(0, "Review")),
                ),
            ),
        )

        val score = MeetingNoteEvaluationScorer.score(case, generated(invented))

        assertEquals(EvaluationCheck.FAIL, score.missingFieldHandling)
        assertEquals(EvaluationCheck.PASS, score.sourceResolution)
    }

    @Test
    fun `expected invalid output scores rejection without semantic denominators`() {
        val case = MeetingNoteEvaluationCase(
            id = "invalid-schema",
            extraction = extraction(),
            expectation = MeetingNoteEvaluationExpectation(
                schemaValid = false,
                content = null,
                rejection = MeetingNoteGenerationRejection.INVALID_OUTPUT,
            ),
        )

        val score = MeetingNoteEvaluationScorer.score(
            case,
            MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.INVALID_OUTPUT),
        )

        assertEquals(EvaluationCheck.PASS, score.schemaValidity)
        assertEquals(EvaluationCheck.NOT_APPLICABLE, score.fieldCorrectness)
        assertEquals(EvaluationCheck.NOT_APPLICABLE, score.sourceResolution)
        assertEquals(EvaluationCheck.PASS, score.expectedOutcome)
    }

    @Test
    fun `summary keeps applicable denominators and rejects duplicate case IDs`() {
        val positive = MeetingNoteEvaluationScorer.score(
            acceptedCase("summary-positive"),
            generated(acceptedCase("summary-positive").expectation.content!!),
        )
        val negativeCase = MeetingNoteEvaluationCase(
            "summary-negative",
            extraction(),
            MeetingNoteEvaluationExpectation(false, null, MeetingNoteGenerationRejection.INVALID_OUTPUT),
        )
        val negative = MeetingNoteEvaluationScorer.score(
            negativeCase,
            MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.INVALID_OUTPUT),
        )

        val summary = MeetingNoteEvaluationScorer.summarize(listOf(positive, negative))

        assertEquals(2, summary.schemaValidity.total)
        assertEquals(1, summary.fieldCorrectness.total)
        assertEquals(2, summary.expectedOutcome.passed)
        assertFailsWith<IllegalArgumentException> {
            MeetingNoteEvaluationScorer.summarize(listOf(positive, positive))
        }
    }
}

private fun acceptedCase(id: String): MeetingNoteEvaluationCase = MeetingNoteEvaluationCase(
    id = id,
    extraction = extraction(),
    expectation = MeetingNoteEvaluationExpectation(true, expectedContent(), null),
)

private fun extraction(): DocumentExtraction = DocumentExtraction(
    sourceDocumentId = DocumentId("source"),
    processor = "fixture-ocr-v1",
    processedAtEpochMilliseconds = 1,
    pages = listOf(ExtractedDocumentPage(0, "Decision: Ship\nAction: Review")),
)

private fun expectedContent(): MeetingNoteContent = MeetingNoteContent(
    title = null,
    decisions = listOf(MeetingDecision("Ship", listOf(MeetingNoteSourceReference(0, "Ship")))),
    actionItems = listOf(MeetingActionItem("Review", null, null, listOf(MeetingNoteSourceReference(0, "Review")))),
)

private fun generated(content: MeetingNoteContent): MeetingNoteGenerationOutcome.Generated =
    MeetingNoteGenerationOutcome.Generated(
        MeetingNoteRecord(
            id = MeetingNoteId("note"),
            sourceDocumentId = DocumentId("source"),
            templateVersion = "meeting-note-v1",
            modelId = "fixture-model",
            generatedContent = content,
            editedContent = null,
            createdAtEpochMilliseconds = 1,
            updatedAtEpochMilliseconds = 1,
            revision = 0,
        ),
    )
