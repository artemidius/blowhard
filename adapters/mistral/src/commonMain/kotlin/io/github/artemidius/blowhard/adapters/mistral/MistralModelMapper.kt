package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.AvailableModel
import io.github.artemidius.blowhard.core.domain.CapabilitySupport
import io.github.artemidius.blowhard.core.domain.ModelAvailability
import io.github.artemidius.blowhard.core.domain.ModelCapabilities

internal fun MistralModelsResponseDto.toDomainModels(): List<AvailableModel>? {
    val mapped = data.map { it.toDomainModel() ?: return null }
    return mapped.takeIf { models -> models.map(AvailableModel::id).distinct().size == models.size }
        ?.sortedBy(AvailableModel::id)
}

private fun MistralModelDto.toDomainModel(): AvailableModel? {
    if (id.isBlank() || (maxContextLength != null && maxContextLength <= 0)) return null
    val providerCapabilities = capabilities
    return AvailableModel(
        id = id,
        aliases = aliases.filter(String::isNotBlank).distinct(),
        contextWindowTokens = maxContextLength,
        // Base model cards omit `archived`; this catalog lists models available to the account.
        availability = if (archived == true) ModelAvailability.ARCHIVED else ModelAvailability.AVAILABLE,
        capabilities = ModelCapabilities(
            chatCompletion = providerCapabilities?.chatCompletion.toSupport(),
            fillInMiddle = providerCapabilities?.fillInMiddle.toSupport(),
            functionCalling = providerCapabilities?.functionCalling.toSupport(),
            fineTuning = providerCapabilities?.fineTuning.toSupport(),
            vision = providerCapabilities?.vision.toSupport(),
            classification = providerCapabilities?.classification.toSupport(),
        ),
    )
}

private fun Boolean?.toSupport(): CapabilitySupport = when (this) {
    true -> CapabilitySupport.SUPPORTED
    false -> CapabilitySupport.UNSUPPORTED
    null -> CapabilitySupport.UNKNOWN
}
