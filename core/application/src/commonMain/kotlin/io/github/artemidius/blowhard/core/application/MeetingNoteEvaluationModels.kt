package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection

/** One versioned, synthetic evaluation case and its hand-reviewed expectation. */
public data class MeetingNoteEvaluationCase(
    public val id: String,
    public val extraction: DocumentExtraction,
    public val expectation: MeetingNoteEvaluationExpectation,
) {
    init {
        require(id.matches(CASE_ID_PATTERN)) { "Evaluation case ID is invalid" }
    }
}

/** Expected validation and semantic outcome for one evaluation case. */
public data class MeetingNoteEvaluationExpectation(
    public val schemaValid: Boolean,
    public val content: MeetingNoteContent?,
    public val rejection: MeetingNoteGenerationRejection?,
) {
    init {
        require((content == null) != (rejection == null)) { "Exactly one evaluation outcome is required" }
        require(schemaValid == (content != null)) { "Schema validity must agree with expected content" }
    }
}

/** Independently scored dimensions for one observed generation outcome. */
public data class MeetingNoteEvaluationScore(
    public val caseId: String,
    public val schemaValidity: EvaluationCheck,
    public val fieldCorrectness: EvaluationCheck,
    public val missingFieldHandling: EvaluationCheck,
    public val sourceResolution: EvaluationCheck,
    public val expectedOutcome: EvaluationCheck,
    public val observedRejection: MeetingNoteGenerationRejection?,
)

/** One scored dimension; inapplicable checks do not inflate aggregate success. */
public enum class EvaluationCheck {
    PASS,
    FAIL,
    NOT_APPLICABLE,
}

/** Deterministic aggregate counts for one evaluation dimension. */
public data class EvaluationMetric(public val passed: Int, public val failed: Int) {
    init {
        require(passed >= 0 && failed >= 0) { "Evaluation counts must not be negative" }
    }

    /** Number of cases for which this dimension was applicable. */
    public val total: Int get() = passed + failed
}

/** Aggregate report data before a runner adds execution metadata or serialization. */
public data class MeetingNoteEvaluationSummary(
    public val sampleCount: Int,
    public val schemaValidity: EvaluationMetric,
    public val fieldCorrectness: EvaluationMetric,
    public val missingFieldHandling: EvaluationMetric,
    public val sourceResolution: EvaluationMetric,
    public val expectedOutcome: EvaluationMetric,
) {
    init {
        require(sampleCount > 0) { "Evaluation summary requires at least one sample" }
    }
}

private val CASE_ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
