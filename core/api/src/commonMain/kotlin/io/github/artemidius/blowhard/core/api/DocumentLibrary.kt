package io.github.artemidius.blowhard.core.api

/** Path-free source selected through a platform-controlled document picker. */
public interface DocumentImportSource {
    /** User-facing name supplied by the platform picker. */
    public val displayName: String

    /** Picker-reported byte size, when available. */
    public val declaredSizeBytes: Long?

    /** Reads at most [maximumBytes] without exposing a platform path. */
    public suspend fun read(maximumBytes: Long): DocumentReadResult
}

/** Bounded result of reading one selected document. */
public sealed interface DocumentReadResult {
    /** Selected content, copied on construction and access. */
    public class Content(bytes: ByteArray) : DocumentReadResult {
        private val value: ByteArray = bytes.copyOf()

        /** Returns an isolated copy of the selected bytes. */
        public fun copyBytes(): ByteArray = value.copyOf()
    }

    /** The source exceeded the supplied read bound. */
    public data object TooLarge : DocumentReadResult

    /** The selection was missing, revoked, unreadable, or otherwise unavailable. */
    public data object Unavailable : DocumentReadResult
}

/** Client-facing lifecycle operations for app-managed local documents. */
public interface DocumentLibrary {
    /** Validates and imports one selected source into app-managed storage. */
    public suspend fun import(source: DocumentImportSource): DocumentImportResult

    /** Lists durable document metadata in store-defined order. */
    public suspend fun list(): DocumentListResult

    /** Looks up one durable document without reading its content. */
    public suspend fun find(id: DocumentId): DocumentLookupResult

    /** Deletes local metadata and content owned by [id]. */
    public suspend fun delete(id: DocumentId): DocumentDeletionResult
}

/** Safe outcome of a bounded local document import. */
public sealed interface DocumentImportResult {
    /** The source was inspected and stored locally. */
    public data class Imported(public val document: ImportedDocument) : DocumentImportResult

    /** The source was rejected or could not be stored. */
    public data class Rejected(public val reason: DocumentImportRejection) : DocumentImportResult
}

/** Content-safe reason why a document import did not complete. */
public enum class DocumentImportRejection {
    INVALID_NAME,
    EMPTY_CONTENT,
    TOO_LARGE,
    UNSUPPORTED_FORMAT,
    INVALID_CONTENT,
    PAGE_LIMIT_EXCEEDED,
    PIXEL_LIMIT_EXCEEDED,
    SOURCE_UNAVAILABLE,
    STORAGE_UNAVAILABLE,
}

/** Safe result of listing local document metadata. */
public sealed interface DocumentListResult {
    /** Durable metadata was loaded successfully. */
    public data class Available(public val documents: List<ImportedDocument>) : DocumentListResult

    /** Durable metadata could not be read safely. */
    public data object StorageUnavailable : DocumentListResult
}

/** Safe result of looking up local document metadata. */
public sealed interface DocumentLookupResult {
    /** Metadata was found for the requested identity. */
    public data class Found(public val document: ImportedDocument) : DocumentLookupResult

    /** No durable document matched the requested identity. */
    public data object NotFound : DocumentLookupResult

    /** Durable metadata could not be read safely. */
    public data object StorageUnavailable : DocumentLookupResult
}

/** Safe outcome of deleting one locally owned document. */
public enum class DocumentDeletionResult {
    DELETED,
    NOT_FOUND,
    STORAGE_UNAVAILABLE,
}
