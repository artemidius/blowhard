package io.github.artemidius.blowhard.core.api

/** OCR output retained for one immutable imported document version. */
public data class DocumentExtraction(
    public val sourceDocumentId: DocumentId,
    public val processor: String,
    public val processedAtEpochMilliseconds: Long,
    public val pages: List<ExtractedDocumentPage>,
) {
    init {
        require(processor.isNotBlank() && processor.length <= MAX_PROCESSOR_LENGTH) {
            "Processor identity is invalid"
        }
        require(processedAtEpochMilliseconds >= 0) { "Processing time must not be negative" }
        require(pages.isNotEmpty()) { "Extraction must preserve at least one source page" }
        require(pages.map(ExtractedDocumentPage::index) == pages.indices.toList()) {
            "Extraction pages must be ordered and contiguous"
        }
    }

    /** Validation bounds shared by persistence and processing adapters. */
    public companion object {
        /** Maximum number of Kotlin characters accepted for a processor identity. */
        public const val MAX_PROCESSOR_LENGTH: Int = 128
    }
}

/** Extracted Markdown associated with one zero-based source page. */
public data class ExtractedDocumentPage(public val index: Int, public val markdown: String) {
    init {
        require(index >= 0) { "Page index must not be negative" }
    }
}

/** Application-owned OCR operations for imported documents. */
public interface DocumentOcr {
    /** Processes one immutable local document and durably replaces its prior extraction. */
    public suspend fun process(id: DocumentId): DocumentProcessingResult

    /** Finds the last durable extraction for [id]. */
    public suspend fun find(id: DocumentId): DocumentExtractionLookupResult
}

/** Safe terminal outcome of one explicit OCR request. */
public sealed interface DocumentProcessingResult {
    /** A validated extraction was stored. */
    public data class Processed(public val extraction: DocumentExtraction) : DocumentProcessingResult

    /** Processing did not publish a new extraction. */
    public data class Rejected(public val reason: DocumentProcessingRejection) : DocumentProcessingResult
}

/** Content-safe reasons why explicit document processing did not complete. */
public enum class DocumentProcessingRejection {
    DOCUMENT_NOT_FOUND,
    CONTENT_UNAVAILABLE,
    PROCESSOR_UNAVAILABLE,
    OUTPUT_INVALID,
    OUTPUT_TOO_LARGE,
    STORAGE_UNAVAILABLE,
}

/** Safe lookup result for one durable extraction. */
public sealed interface DocumentExtractionLookupResult {
    /** A durable extraction exists for the requested source identity. */
    public data class Found(public val extraction: DocumentExtraction) : DocumentExtractionLookupResult

    /** The document exists but has no durable extraction. */
    public data object NotProcessed : DocumentExtractionLookupResult

    /** The requested source document does not exist. */
    public data object DocumentNotFound : DocumentExtractionLookupResult

    /** Local processing storage could not be read safely. */
    public data object StorageUnavailable : DocumentExtractionLookupResult
}
