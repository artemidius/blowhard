package io.github.artemidius.blowhard.core.api

/** Immutable generation source captured when an application accepts a run. */
public sealed interface GenerationTarget {
    /** Deterministic bundled generation with no external request. */
    public data object Demo : GenerationTarget

    /** External generation through the verified model selected for this attempt. */
    public data class Live(public val modelId: String) : GenerationTarget {
        init {
            require(modelId.isNotBlank()) { "Model identifier must not be blank" }
            require(modelId.length <= MAX_MODEL_ID_LENGTH) { "Model identifier is too long" }
            require(modelId.none { it.isWhitespace() || it.isISOControl() }) {
                "Model identifier contains unsafe characters"
            }
        }
    }
}

private const val MAX_MODEL_ID_LENGTH = 256
