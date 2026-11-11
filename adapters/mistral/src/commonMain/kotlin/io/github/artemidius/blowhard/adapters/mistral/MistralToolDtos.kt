package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class MistralToolRequestDto(
    val model: String,
    val messages: List<MistralToolMessageDto>,
    val tools: List<MistralToolDto>,
    @SerialName("tool_choice") val toolChoice: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean,
)

@Serializable
internal data class MistralToolDto(val type: String, val function: MistralFunctionDefinitionDto)

@Serializable
internal data class MistralFunctionDefinitionDto(val name: String, val description: String, val parameters: JsonObject)

@Serializable
internal data class MistralToolMessageDto(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<MistralToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
internal data class MistralToolCallDto(val id: String?, val type: String?, val function: MistralFunctionCallDto?)

@Serializable
internal data class MistralFunctionCallDto(val name: String?, val arguments: String?)

@Serializable
internal data class MistralToolResponseDto(val choices: List<MistralToolChoiceDto>)

@Serializable
internal data class MistralToolChoiceDto(
    val index: Int,
    val message: MistralToolMessageDto,
    @SerialName("finish_reason") val finishReason: String?,
)
