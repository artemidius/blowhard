package io.github.artemidius.blowhard.core.api

/** Supported local document formats after content inspection. */
public enum class DocumentFormat {
    PDF,
    PNG,
    JPEG,
}

/** Content characteristics verified during import. */
public sealed interface DocumentContentDescriptor {
    /** Verified document format. */
    public val format: DocumentFormat

    /** Page-bounded PDF content. */
    public data class Pdf(public val pageCount: Int) : DocumentContentDescriptor {
        override val format: DocumentFormat = DocumentFormat.PDF

        init {
            require(pageCount > 0) { "PDF page count must be positive" }
        }
    }

    /** Pixel-bounded raster image content. */
    public data class Image(
        override val format: DocumentFormat,
        public val widthPixels: Int,
        public val heightPixels: Int,
    ) : DocumentContentDescriptor {
        init {
            require(format == DocumentFormat.PNG || format == DocumentFormat.JPEG) {
                "Image content must use an image format"
            }
            require(widthPixels > 0 && heightPixels > 0) { "Image dimensions must be positive" }
        }
    }
}

/** Durable metadata for one app-managed local document. */
public data class ImportedDocument(
    public val id: DocumentId,
    public val displayName: String,
    public val sizeBytes: Long,
    public val importedAtEpochMilliseconds: Long,
    public val content: DocumentContentDescriptor,
) {
    init {
        require(displayName.isNotBlank()) { "Document name must not be blank" }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) { "Document name is too long" }
        require(sizeBytes > 0) { "Document size must be positive" }
        require(importedAtEpochMilliseconds >= 0) { "Import time must not be negative" }
    }

    /** Shared validation bounds for imported document metadata. */
    public companion object {
        /** Maximum number of Kotlin characters accepted for a display name. */
        public const val MAX_DISPLAY_NAME_LENGTH: Int = 255
    }
}
