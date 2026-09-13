package io.github.artemidius.blowhard.adapters.mistral

/** Validated provider settings for one Mistral text-generation adapter. */
public data class MistralChatConfiguration(public val modelId: String, public val maxOutputTokens: Int? = null) {
    init {
        require(modelId.isNotBlank()) { "Model identifier must not be blank" }
        require(modelId.length <= MAX_MODEL_ID_LENGTH) { "Model identifier is too long" }
        require(
            modelId.none {
                it.isWhitespace() || it.isISOControl()
            },
        ) { "Model identifier contains unsafe characters" }
        require(maxOutputTokens == null || maxOutputTokens in 1..MAX_OUTPUT_TOKENS) {
            "Maximum output tokens are outside the supported range"
        }
    }
}

private const val MAX_MODEL_ID_LENGTH = 256
private const val MAX_OUTPUT_TOKENS = 1_000_000
