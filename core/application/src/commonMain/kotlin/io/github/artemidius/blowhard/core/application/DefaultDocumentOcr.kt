package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.DocumentProcessingRejection
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrFailureKind
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial, bounded OCR orchestration over local immutable document versions. */
public class DefaultDocumentOcr(
    private val store: DocumentProcessingStore,
    private val processor: DocumentOcrPort,
    private val clock: DocumentProcessingClock,
    policy: DocumentOcrPolicy = DocumentOcrPolicy(),
) : DocumentOcr {
    private val processingMutex = Mutex()
    private val outputValidator = DocumentOcrOutputValidator(policy)

    override suspend fun process(id: DocumentId): DocumentProcessingResult = processingMutex.withLock {
        processSafely(id)
    }

    override suspend fun find(id: DocumentId): DocumentExtractionLookupResult = try {
        when (val result = store.findExtraction(id)) {
            is DocumentExtractionStoreLookupResult.Found -> DocumentExtractionLookupResult.Found(result.extraction)
            DocumentExtractionStoreLookupResult.NotProcessed -> DocumentExtractionLookupResult.NotProcessed
            DocumentExtractionStoreLookupResult.SourceNotFound -> DocumentExtractionLookupResult.DocumentNotFound
            DocumentExtractionStoreLookupResult.Unavailable -> DocumentExtractionLookupResult.StorageUnavailable
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentExtractionLookupResult.StorageUnavailable
    }

    private suspend fun processSafely(id: DocumentId): DocumentProcessingResult = when (val source = readSource(id)) {
        is DocumentSourceLookupResult.Found -> processSource(source)
        DocumentSourceLookupResult.NotFound -> rejected(DocumentProcessingRejection.DOCUMENT_NOT_FOUND)
        DocumentSourceLookupResult.Unavailable -> rejected(DocumentProcessingRejection.CONTENT_UNAVAILABLE)
    }

    private suspend fun processSource(source: DocumentSourceLookupResult.Found): DocumentProcessingResult {
        val request = DocumentOcrRequest(source.source.document, source.source.copyContent())
        return when (val result = process(request)) {
            is DocumentOcrPortResult.Processed -> validateAndSave(source, result)
            is DocumentOcrPortResult.Failed -> rejected(result.kind.toRejection())
        }
    }

    private suspend fun validateAndSave(
        source: DocumentSourceLookupResult.Found,
        result: DocumentOcrPortResult.Processed,
    ): DocumentProcessingResult = when (val processedAt = processingTime()) {
        null -> rejected(DocumentProcessingRejection.PROCESSOR_UNAVAILABLE)
        else -> validateAndSave(source, result, processedAt)
    }

    private suspend fun validateAndSave(
        source: DocumentSourceLookupResult.Found,
        result: DocumentOcrPortResult.Processed,
        processedAtEpochMilliseconds: Long,
    ): DocumentProcessingResult = when (
        val validation = outputValidator.validate(
            document = source.source.document,
            result = result,
            processedAtEpochMilliseconds = processedAtEpochMilliseconds,
        )
    ) {
        is DocumentOcrOutputValidation.Accepted -> when (save(validation.extraction)) {
            DocumentExtractionSaveResult.SAVED -> DocumentProcessingResult.Processed(validation.extraction)
            DocumentExtractionSaveResult.SOURCE_NOT_FOUND -> rejected(DocumentProcessingRejection.DOCUMENT_NOT_FOUND)
            DocumentExtractionSaveResult.UNAVAILABLE -> rejected(DocumentProcessingRejection.STORAGE_UNAVAILABLE)
        }

        is DocumentOcrOutputValidation.Rejected -> rejected(validation.reason)
    }

    private fun processingTime(): Long? = try {
        clock.nowEpochMilliseconds()
    } catch (_: Exception) {
        null
    }

    private suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = try {
        store.readSource(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentSourceLookupResult.Unavailable
    }

    private suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult = try {
        processor.process(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentOcrPortResult.Failed(DocumentOcrFailureKind.REMOTE_FAILURE)
    }

    private suspend fun save(extraction: DocumentExtraction): DocumentExtractionSaveResult = try {
        store.saveExtraction(extraction)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentExtractionSaveResult.UNAVAILABLE
    }

    private companion object {
        fun DocumentOcrFailureKind.toRejection(): DocumentProcessingRejection = when (this) {
            DocumentOcrFailureKind.INPUT_REJECTED, DocumentOcrFailureKind.INVALID_RESPONSE ->
                DocumentProcessingRejection.OUTPUT_INVALID

            DocumentOcrFailureKind.AUTHENTICATION,
            DocumentOcrFailureKind.RATE_LIMITED,
            DocumentOcrFailureKind.REMOTE_FAILURE,
            -> DocumentProcessingRejection.PROCESSOR_UNAVAILABLE
        }

        fun rejected(reason: DocumentProcessingRejection): DocumentProcessingResult =
            DocumentProcessingResult.Rejected(reason)
    }
}
