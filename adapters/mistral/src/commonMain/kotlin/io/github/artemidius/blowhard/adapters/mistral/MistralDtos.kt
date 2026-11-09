package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MistralModelsResponseDto(val data: List<MistralModelDto>)

@Serializable
internal data class MistralModelDto(
    val id: String,
    val aliases: List<String> = emptyList(),
    @SerialName("max_context_length") val maxContextLength: Long? = null,
    val archived: Boolean? = null,
    val capabilities: MistralCapabilitiesDto? = null,
)

@Serializable
internal data class MistralCapabilitiesDto(
    @SerialName("completion_chat") val chatCompletion: Boolean? = null,
    @SerialName("completion_fim") val fillInMiddle: Boolean? = null,
    @SerialName("function_calling") val functionCalling: Boolean? = null,
    @SerialName("fine_tuning") val fineTuning: Boolean? = null,
    val vision: Boolean? = null,
    val classification: Boolean? = null,
)
