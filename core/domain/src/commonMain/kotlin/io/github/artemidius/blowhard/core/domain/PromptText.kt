package io.github.artemidius.blowhard.core.domain

import kotlin.jvm.JvmInline

/** Validated user-authored input for a generation request. */
@JvmInline
public value class PromptText private constructor(public val value: String) {
    /** Creates validated prompt values from untrusted text. */
    public companion object {
        /** Validates [raw] without modifying its whitespace or contents. */
        public fun from(raw: String): PromptTextResult = if (raw.isBlank()) {
            PromptTextResult.Rejected(PromptRejection.BLANK)
        } else {
            PromptTextResult.Accepted(PromptText(raw))
        }
    }
}

/** Result of converting untrusted text into [PromptText]. */
public sealed interface PromptTextResult {
    /** Contains validated prompt text. */
    public data class Accepted(public val prompt: PromptText) : PromptTextResult

    /** Describes why the input cannot become a prompt. */
    public data class Rejected(public val reason: PromptRejection) : PromptTextResult
}

/** Reasons why prompt text is rejected before generation starts. */
public enum class PromptRejection {
    /** The input contained no non-whitespace characters. */
    BLANK,
}
