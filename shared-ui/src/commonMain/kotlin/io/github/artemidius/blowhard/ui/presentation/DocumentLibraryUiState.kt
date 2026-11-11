package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.ImportedDocument

/** Immutable document-library presentation state. */
public data class DocumentLibraryUiState(
    public val documents: List<ImportedDocument> = emptyList(),
    public val isBusy: Boolean = false,
    public val pendingDeletion: ImportedDocument? = null,
    public val notice: DocumentLibraryNotice? = null,
)

/** Sanitized, local-only feedback for document lifecycle operations. */
public sealed interface DocumentLibraryNotice {
    /** One document was imported successfully. */
    public data class Imported(public val displayName: String) : DocumentLibraryNotice

    /** The selected source was rejected before durable storage. */
    public data class ImportRejected(public val reason: DocumentImportRejection) : DocumentLibraryNotice

    /** One locally owned document was deleted. */
    public data class Deleted(public val displayName: String) : DocumentLibraryNotice

    /** Local storage or platform selection could not complete safely. */
    public data object OperationUnavailable : DocumentLibraryNotice
}
