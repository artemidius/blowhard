package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal class MistralMeetingNoteRequestDto(
    val model: String,
    val messages: List<MistralMeetingNoteMessageDto>,
    @SerialName("response_format") val responseFormat: MistralStructuredResponseFormatDto,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean,
)

@Serializable
internal class MistralMeetingNoteMessageDto(val role: String, val content: String)

@Serializable
internal class MistralStructuredResponseFormatDto(
    val type: String,
    @SerialName("json_schema") val jsonSchema: MistralJsonSchemaDto,
)

@Serializable
internal class MistralJsonSchemaDto(val name: String, val schema: JsonObject, val strict: Boolean)

@Serializable
internal class MistralMeetingNoteResponseDto(val model: String?, val choices: List<MistralMeetingNoteChoiceDto>)

@Serializable
internal class MistralMeetingNoteChoiceDto(
    val index: Int,
    val message: MistralMeetingNoteResponseMessageDto,
    @SerialName("finish_reason") val finishReason: String?,
)

@Serializable
internal class MistralMeetingNoteResponseMessageDto(val content: JsonElement?)
