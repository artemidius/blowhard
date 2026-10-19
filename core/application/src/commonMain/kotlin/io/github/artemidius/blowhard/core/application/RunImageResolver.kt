package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.RunAttachment
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.GenerationImage
import io.github.artemidius.blowhard.core.domain.GenerationImageMediaType
import io.github.artemidius.blowhard.core.domain.StoredDocumentSource

internal class RunImageResolver(private val store: DocumentProcessingStore?) {
    suspend fun resolve(attachment: RunAttachment?, supportsVision: Boolean): ImageResolution = when {
        attachment == null -> ImageResolution.Accepted(null)
        !supportsVision -> ImageResolution.Rejected(StartRunRejection.MODEL_DOES_NOT_SUPPORT_VISION)
        store == null -> ImageResolution.Rejected(StartRunRejection.ATTACHMENT_NOT_AVAILABLE)
        else -> store.readSource(attachment.documentId).toImageResolution()
    }
}

internal sealed interface ImageResolution {
    data class Accepted(val image: GenerationImage?) : ImageResolution

    data class Rejected(val reason: StartRunRejection) : ImageResolution
}

private fun DocumentSourceLookupResult.toImageResolution(): ImageResolution = when (this) {
    is DocumentSourceLookupResult.Found -> source.toImageResolution()

    DocumentSourceLookupResult.NotFound,
    DocumentSourceLookupResult.Unavailable,
    -> ImageResolution.Rejected(StartRunRejection.ATTACHMENT_NOT_AVAILABLE)
}

private fun StoredDocumentSource.toImageResolution(): ImageResolution {
    val mediaType = (document.content as? DocumentContentDescriptor.Image)?.format?.toMediaType()
    val image = mediaType?.let { GenerationImage.from(it, copyContent()) }
    return image?.let(ImageResolution::Accepted)
        ?: ImageResolution.Rejected(StartRunRejection.ATTACHMENT_NOT_SUPPORTED)
}

private fun DocumentFormat.toMediaType(): GenerationImageMediaType? = when (this) {
    DocumentFormat.PNG -> GenerationImageMediaType.PNG
    DocumentFormat.JPEG -> GenerationImageMediaType.JPEG
    DocumentFormat.PDF -> null
}
