package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference

/** Scores typed meeting-note outcomes without treating valid citations as semantic proof. */
public object MeetingNoteEvaluationScorer {
    /** Scores one observed result against a hand-reviewed synthetic expectation. */
    public fun score(
        case: MeetingNoteEvaluationCase,
        outcome: MeetingNoteGenerationOutcome,
    ): MeetingNoteEvaluationScore {
        val observedContent = (outcome as? MeetingNoteGenerationOutcome.Generated)?.note?.content
        val expected = case.expectation
        return MeetingNoteEvaluationScore(
            caseId = case.id,
            schemaValidity = compareSchemaValidity(expected.schemaValid, outcome),
            fieldCorrectness = compareContent(expected.content, observedContent),
            missingFieldHandling = compareMissingFields(expected.content, observedContent),
            sourceResolution = observedContent?.let { check(it.sourcesResolve(case)) }
                ?: EvaluationCheck.NOT_APPLICABLE,
            expectedOutcome = compareOutcome(expected, outcome),
            observedRejection = (outcome as? MeetingNoteGenerationOutcome.Rejected)?.reason,
        )
    }

    /** Aggregates applicable checks while preserving their individual denominators. */
    public fun summarize(scores: List<MeetingNoteEvaluationScore>): MeetingNoteEvaluationSummary {
        require(scores.isNotEmpty()) { "Evaluation scores must not be empty" }
        require(scores.map { it.caseId }.distinct().size == scores.size) { "Evaluation case IDs must be unique" }
        return MeetingNoteEvaluationSummary(
            sampleCount = scores.size,
            schemaValidity = scores.metric(MeetingNoteEvaluationScore::schemaValidity),
            fieldCorrectness = scores.metric(MeetingNoteEvaluationScore::fieldCorrectness),
            missingFieldHandling = scores.metric(MeetingNoteEvaluationScore::missingFieldHandling),
            sourceResolution = scores.metric(MeetingNoteEvaluationScore::sourceResolution),
            expectedOutcome = scores.metric(MeetingNoteEvaluationScore::expectedOutcome),
        )
    }
}

private fun compareSchemaValidity(expectedValid: Boolean, observed: MeetingNoteGenerationOutcome): EvaluationCheck =
    when (observed) {
        is MeetingNoteGenerationOutcome.Generated -> check(expectedValid)

        is MeetingNoteGenerationOutcome.Rejected -> if (
            observed.reason == io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection.INVALID_OUTPUT
        ) {
            check(!expectedValid)
        } else {
            EvaluationCheck.NOT_APPLICABLE
        }
    }

private fun compareContent(expected: MeetingNoteContent?, observed: MeetingNoteContent?): EvaluationCheck = when {
    expected == null -> EvaluationCheck.NOT_APPLICABLE
    observed == null -> EvaluationCheck.FAIL
    else -> check(expected == observed)
}

private fun compareMissingFields(expected: MeetingNoteContent?, observed: MeetingNoteContent?): EvaluationCheck = when {
    expected == null -> EvaluationCheck.NOT_APPLICABLE
    observed == null -> EvaluationCheck.FAIL
    else -> check(expected.missingFieldShape() == observed.missingFieldShape())
}

private fun compareOutcome(
    expected: MeetingNoteEvaluationExpectation,
    observed: MeetingNoteGenerationOutcome,
): EvaluationCheck = when (observed) {
    is MeetingNoteGenerationOutcome.Generated -> check(expected.content != null)
    is MeetingNoteGenerationOutcome.Rejected -> check(expected.rejection == observed.reason)
}

private fun MeetingNoteContent.missingFieldShape(): List<Boolean> = buildList {
    add(title == null)
    actionItems.forEach { action -> addAll(action.missingFieldShape()) }
}

private fun MeetingActionItem.missingFieldShape(): List<Boolean> = listOf(owner == null, dueDate == null)

private fun MeetingNoteContent.sourcesResolve(case: MeetingNoteEvaluationCase): Boolean =
    (decisions.flatMap { it.sources } + actionItems.flatMap { it.sources })
        .all { it.resolves(case) }

private fun MeetingNoteSourceReference.resolves(case: MeetingNoteEvaluationCase): Boolean =
    case.extraction.pages.getOrNull(pageIndex)?.markdown?.contains(excerpt) == true

private fun List<MeetingNoteEvaluationScore>.metric(
    selector: (MeetingNoteEvaluationScore) -> EvaluationCheck,
): EvaluationMetric = EvaluationMetric(
    passed = count { selector(it) == EvaluationCheck.PASS },
    failed = count { selector(it) == EvaluationCheck.FAIL },
)

private fun check(value: Boolean): EvaluationCheck = if (value) EvaluationCheck.PASS else EvaluationCheck.FAIL
