package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument

/** Atomic local ownership boundary for document metadata and app-managed bytes. */
public interface DocumentStore {
    /** Stores [document] and [content] together without retaining the caller's mutable array. */
    public suspend fun save(document: ImportedDocument, content: ByteArray): DocumentSaveResult

    /** Lists document metadata in deterministic store-defined order. */
    public suspend fun list(): DocumentStoreListResult

    /** Finds document metadata without exposing stored content. */
    public suspend fun find(id: DocumentId): DocumentStoreLookupResult

    /** Deletes metadata and content as one logical operation. */
    public suspend fun delete(id: DocumentId): DocumentStoreDeletionResult

    /** Releases local storage resources. Repeated calls have no effect. */
    public suspend fun close()
}

/** Atomic document save outcome. */
public enum class DocumentSaveResult {
    SAVED,
    ID_CONFLICT,
    UNAVAILABLE,
}

/** Durable metadata listing outcome. */
public sealed interface DocumentStoreListResult {
    /** Metadata was loaded in deterministic order. */
    public data class Available(public val documents: List<ImportedDocument>) : DocumentStoreListResult

    /** Metadata could not be read safely. */
    public data object Unavailable : DocumentStoreListResult
}

/** Durable metadata lookup outcome. */
public sealed interface DocumentStoreLookupResult {
    /** Metadata was found for the requested identity. */
    public data class Found(public val document: ImportedDocument) : DocumentStoreLookupResult

    /** No durable document matched the requested identity. */
    public data object NotFound : DocumentStoreLookupResult

    /** Metadata could not be read safely. */
    public data object Unavailable : DocumentStoreLookupResult
}

/** Atomic document deletion outcome. */
public enum class DocumentStoreDeletionResult {
    DELETED,
    NOT_FOUND,
    UNAVAILABLE,
}
