package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult
import io.github.artemidius.blowhard.core.domain.DocumentInspector
import kotlinx.coroutines.CancellationException

internal class DocumentImportPreparer(
    private val inspector: DocumentInspector,
    private val policy: DocumentImportPolicy,
) {
    suspend fun prepare(source: DocumentImportSource): DocumentImportPreparation {
        val sourceRejection = sourceRejection(source)
        val read = sourceRejection?.let(SourceRead::Rejected) ?: read(source)
        return when (read) {
            is SourceRead.Accepted -> prepareContent(source.displayName, read.bytes)
            is SourceRead.Rejected -> DocumentImportPreparation.Rejected(read.reason)
        }
    }

    private suspend fun prepareContent(displayName: String, bytes: ByteArray): DocumentImportPreparation = when {
        bytes.isEmpty() -> DocumentImportPreparation.Rejected(DocumentImportRejection.EMPTY_CONTENT)

        bytes.size.toLong() > policy.maximumBytes ->
            DocumentImportPreparation.Rejected(DocumentImportRejection.TOO_LARGE)

        else -> prepareInspection(displayName, bytes)
    }

    private suspend fun prepareInspection(displayName: String, bytes: ByteArray): DocumentImportPreparation =
        when (val inspection = inspect(bytes)) {
            is DocumentInspectionResult.Inspected -> prepareInspected(displayName, bytes, inspection.descriptor)

            DocumentInspectionResult.UnsupportedFormat ->
                DocumentImportPreparation.Rejected(DocumentImportRejection.UNSUPPORTED_FORMAT)

            DocumentInspectionResult.InvalidContent ->
                DocumentImportPreparation.Rejected(DocumentImportRejection.INVALID_CONTENT)
        }

    private fun prepareInspected(
        displayName: String,
        bytes: ByteArray,
        descriptor: DocumentContentDescriptor,
    ): DocumentImportPreparation {
        val rejection = policyRejection(descriptor)
        return if (rejection == null) {
            DocumentImportPreparation.Accepted(displayName, bytes, descriptor)
        } else {
            DocumentImportPreparation.Rejected(rejection)
        }
    }

    private suspend fun read(source: DocumentImportSource): SourceRead = try {
        when (val result = source.read(policy.maximumBytes)) {
            is DocumentReadResult.Content -> SourceRead.Accepted(result.copyBytes())
            DocumentReadResult.TooLarge -> SourceRead.Rejected(DocumentImportRejection.TOO_LARGE)
            DocumentReadResult.Unavailable -> SourceRead.Rejected(DocumentImportRejection.SOURCE_UNAVAILABLE)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SourceRead.Rejected(DocumentImportRejection.SOURCE_UNAVAILABLE)
    }

    private suspend fun inspect(bytes: ByteArray): DocumentInspectionResult = try {
        inspector.inspect(bytes.copyOf())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentInspectionResult.InvalidContent
    }

    private fun sourceRejection(source: DocumentImportSource): DocumentImportRejection? {
        val validName = source.displayName.isNotBlank() &&
            source.displayName.length <= ImportedDocument.MAX_DISPLAY_NAME_LENGTH
        val declaredSize = source.declaredSizeBytes
        return when {
            !validName -> DocumentImportRejection.INVALID_NAME

            declaredSize != null && (declaredSize < 0 || declaredSize > policy.maximumBytes) ->
                DocumentImportRejection.TOO_LARGE

            else -> null
        }
    }

    private fun policyRejection(descriptor: DocumentContentDescriptor): DocumentImportRejection? = when (descriptor) {
        is DocumentContentDescriptor.Pdf -> if (descriptor.pageCount > policy.maximumPdfPages) {
            DocumentImportRejection.PAGE_LIMIT_EXCEEDED
        } else {
            null
        }

        is DocumentContentDescriptor.Image -> {
            val pixels = descriptor.widthPixels.toLong() * descriptor.heightPixels.toLong()
            if (pixels > policy.maximumImagePixels) DocumentImportRejection.PIXEL_LIMIT_EXCEEDED else null
        }
    }

    private sealed interface SourceRead {
        data class Accepted(val bytes: ByteArray) : SourceRead

        data class Rejected(val reason: DocumentImportRejection) : SourceRead
    }
}

internal sealed interface DocumentImportPreparation {
    data class Accepted(val displayName: String, val bytes: ByteArray, val descriptor: DocumentContentDescriptor) :
        DocumentImportPreparation

    data class Rejected(val reason: DocumentImportRejection) : DocumentImportPreparation
}
