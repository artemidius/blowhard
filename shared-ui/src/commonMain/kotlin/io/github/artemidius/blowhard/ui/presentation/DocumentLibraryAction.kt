package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentId

/** User intent accepted by [DocumentLibraryPresenter]. */
public sealed interface DocumentLibraryAction {
    /** Reload durable document metadata. */
    public data object RefreshRequested : DocumentLibraryAction

    /** Ask the platform picker for one local document. */
    public data object ImportRequested : DocumentLibraryAction

    /** Open the local workbench for [id] and load its last extraction. */
    public data class OpenRequested(public val id: DocumentId) : DocumentLibraryAction

    /** Close the local workbench without changing durable state. */
    public data object WorkbenchClosed : DocumentLibraryAction

    /** Explicitly submit [id] for OCR and open its validated result. */
    public data class ProcessRequested(public val id: DocumentId) : DocumentLibraryAction

    /** Select one zero-based extracted page. */
    public data class PageSelected(public val index: Int) : DocumentLibraryAction

    /** Show deletion confirmation for [id]. */
    public data class DeleteRequested(public val id: DocumentId) : DocumentLibraryAction

    /** Delete the document currently awaiting confirmation. */
    public data object DeleteConfirmed : DocumentLibraryAction

    /** Keep the document currently awaiting confirmation. */
    public data object DeleteDismissed : DocumentLibraryAction

    /** Clear transient feedback. */
    public data object NoticeDismissed : DocumentLibraryAction
}
