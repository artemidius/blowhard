package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument

/** Local source-content and extraction persistence boundary. */
public interface DocumentProcessingStore {
    /** Reads one immutable imported document and an isolated copy of its bytes. */
    public suspend fun readSource(id: DocumentId): DocumentSourceLookupResult

    /** Atomically replaces the extraction owned by its source document. */
    public suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult

    /** Finds the last durable extraction and distinguishes missing source identity. */
    public suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult

    /** Releases local storage resources. Repeated calls have no effect. */
    public suspend fun close()
}

/** Immutable source content loaded for one processing attempt. */
public class StoredDocumentSource(public val document: ImportedDocument, content: ByteArray) {
    private val value: ByteArray = content.copyOf()

    init {
        require(value.size.toLong() == document.sizeBytes) { "Stored content size does not match metadata" }
    }

    /** Returns an isolated copy of the stored source bytes. */
    public fun copyContent(): ByteArray = value.copyOf()
}

/** Safe source-content lookup result. */
public sealed interface DocumentSourceLookupResult {
    /** Source metadata and content were loaded together. */
    public data class Found(public val source: StoredDocumentSource) : DocumentSourceLookupResult

    /** No imported document matched the requested identity. */
    public data object NotFound : DocumentSourceLookupResult

    /** Source content could not be read safely. */
    public data object Unavailable : DocumentSourceLookupResult
}

/** Safe durable extraction save outcome. */
public enum class DocumentExtractionSaveResult {
    SAVED,
    SOURCE_NOT_FOUND,
    UNAVAILABLE,
}

/** Safe storage lookup for a durable extraction. */
public sealed interface DocumentExtractionStoreLookupResult {
    /** A validated extraction was found. */
    public data class Found(public val extraction: DocumentExtraction) : DocumentExtractionStoreLookupResult

    /** The source exists but has no stored extraction. */
    public data object NotProcessed : DocumentExtractionStoreLookupResult

    /** The source document no longer exists. */
    public data object SourceNotFound : DocumentExtractionStoreLookupResult

    /** Processing storage could not be read safely. */
    public data object Unavailable : DocumentExtractionStoreLookupResult
}
