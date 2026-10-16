package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MistralOcrRequestDto(
    val model: String,
    val document: MistralOcrDocumentDto,
    @SerialName("include_image_base64") val includeImageBase64: Boolean,
    @SerialName("table_format") val tableFormat: String,
)

@Serializable
internal data class MistralOcrDocumentDto(
    val type: String,
    @SerialName("document_url") val documentUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
internal data class MistralOcrResponseDto(val pages: List<MistralOcrPageDto>)

@Serializable
internal data class MistralOcrPageDto(val index: Int, val markdown: String)
