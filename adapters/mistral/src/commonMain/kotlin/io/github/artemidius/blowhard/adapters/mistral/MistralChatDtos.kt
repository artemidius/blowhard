package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.TokenUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class MistralChatRequestDto(
    val model: String,
    val messages: List<MistralChatMessageDto>,
    val stream: Boolean,
    @SerialName("stream_options") val streamOptions: MistralStreamOptionsDto,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
)

@Serializable
internal data class MistralStreamOptionsDto(@SerialName("include_usage") val includeUsage: Boolean)

@Serializable
internal data class MistralChatMessageDto(val role: String, val content: String)

@Serializable
internal data class MistralChatChunkDto(
    val choices: List<MistralChatChoiceDto> = emptyList(),
    val usage: MistralUsageDto? = null,
)

@Serializable
internal data class MistralUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Long? = null,
    @SerialName("completion_tokens") val completionTokens: Long? = null,
    @SerialName("total_tokens") val totalTokens: Long? = null,
) {
    fun toTokenUsage(): TokenUsage? = if (promptTokens == null && completionTokens == null && totalTokens == null) {
        null
    } else {
        TokenUsage(promptTokens, completionTokens, totalTokens)
    }
}

@Serializable
internal data class MistralChatChoiceDto(
    val index: Int,
    val delta: MistralChatDeltaDto,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class MistralChatDeltaDto(val content: JsonElement? = null)

internal data class ParsedMistralChunk(
    val text: String,
    val hasChoice: Boolean,
    val finished: Boolean,
    val usage: TokenUsage?,
)

internal fun MistralChatChunkDto.toParsedChunk(): ParsedMistralChunk? = when {
    choices.isEmpty() ->
        ParsedMistralChunk("", hasChoice = false, finished = false, usage?.toTokenUsage()).takeIf { usage != null }

    choices.size == 1 -> choices.single().toParsedChunk(usage?.toTokenUsage())

    else -> null
}

private fun MistralChatChoiceDto.toParsedChunk(usage: TokenUsage?): ParsedMistralChunk? {
    val text = delta.content.toTextContent()
    return if (index == 0 && text != null) {
        ParsedMistralChunk(text, hasChoice = true, finished = finishReason != null, usage)
    } else {
        null
    }
}

private fun JsonElement?.toTextContent(): String? = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.takeIf { isString }
    is JsonArray -> map { it.toTextChunk() ?: return null }.joinToString(separator = "")
    else -> null
}

private fun JsonElement.toTextChunk(): String? {
    val chunk = this as? JsonObject
    val type = (chunk?.get("type") as? JsonPrimitive)?.contentOrNull
    val text = chunk?.get("text") as? JsonPrimitive
    return text?.contentOrNull?.takeIf { type == "text" && text.isString }
}
