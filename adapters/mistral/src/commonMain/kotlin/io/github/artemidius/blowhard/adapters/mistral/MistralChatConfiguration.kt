package io.github.artemidius.blowhard.adapters.mistral

/** Validated provider settings for one Mistral text-generation adapter. */
public data class MistralChatConfiguration(public val modelId: String) {
    init {
        require(modelId.isNotBlank()) { "Model identifier must not be blank" }
        require(modelId.length <= MAX_MODEL_ID_LENGTH) { "Model identifier is too long" }
        require(
            modelId.none {
                it.isWhitespace() || it.isISOControl()
            },
        ) { "Model identifier contains unsafe characters" }
    }
}

private const val MAX_MODEL_ID_LENGTH = 256
