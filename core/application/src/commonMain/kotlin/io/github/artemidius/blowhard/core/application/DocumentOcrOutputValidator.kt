package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentProcessingRejection
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentOcrPage
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult

internal class DocumentOcrOutputValidator(private val policy: DocumentOcrPolicy) {
    fun validate(
        document: ImportedDocument,
        result: DocumentOcrPortResult.Processed,
        processedAtEpochMilliseconds: Long,
    ): DocumentOcrOutputValidation = validatePages(document.content, result.pages)?.let { pages ->
        createValidation(document, result, processedAtEpochMilliseconds, pages)
    } ?: DocumentOcrOutputValidation.Rejected(outputRejection(result.pages))

    private fun createValidation(
        document: ImportedDocument,
        result: DocumentOcrPortResult.Processed,
        processedAtEpochMilliseconds: Long,
        pages: List<ExtractedDocumentPage>,
    ): DocumentOcrOutputValidation = try {
        DocumentOcrOutputValidation.Accepted(
            DocumentExtraction(document.id, result.processor, processedAtEpochMilliseconds, pages),
        )
    } catch (_: IllegalArgumentException) {
        DocumentOcrOutputValidation.Rejected(DocumentProcessingRejection.OUTPUT_INVALID)
    }

    private fun validatePages(
        descriptor: DocumentContentDescriptor,
        pages: List<DocumentOcrPage>,
    ): List<ExtractedDocumentPage>? {
        val expectedPages = when (descriptor) {
            is DocumentContentDescriptor.Pdf -> descriptor.pageCount
            is DocumentContentDescriptor.Image -> 1
        }
        if (pages.size != expectedPages || pages.size > policy.maximumPages) return null

        var totalCharacters = 0L
        val pagesAreValid = pages.withIndex().all { (expectedIndex, page) ->
            totalCharacters += page.markdown.length
            page.index == expectedIndex &&
                page.markdown.length <= policy.maximumPageCharacters &&
                totalCharacters <= policy.maximumTotalCharacters
        }
        return pages.takeIf { pagesAreValid }
            ?.map { ExtractedDocumentPage(it.index, it.markdown) }
    }

    private fun outputRejection(pages: List<DocumentOcrPage>): DocumentProcessingRejection =
        if (pages.size > policy.maximumPages || pages.any { it.markdown.length > policy.maximumPageCharacters } ||
            pages.sumOf { it.markdown.length.toLong() } > policy.maximumTotalCharacters
        ) {
            DocumentProcessingRejection.OUTPUT_TOO_LARGE
        } else {
            DocumentProcessingRejection.OUTPUT_INVALID
        }
}

internal sealed interface DocumentOcrOutputValidation {
    data class Accepted(val extraction: DocumentExtraction) : DocumentOcrOutputValidation

    data class Rejected(val reason: DocumentProcessingRejection) : DocumentOcrOutputValidation
}
