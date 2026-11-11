package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentId

/** User intent accepted by [DocumentLibraryPresenter]. */
public sealed interface DocumentLibraryAction {
    /** Reload durable document metadata. */
    public data object RefreshRequested : DocumentLibraryAction

    /** Ask the platform picker for one local document. */
    public data object ImportRequested : DocumentLibraryAction

    /** Show deletion confirmation for [id]. */
    public data class DeleteRequested(public val id: DocumentId) : DocumentLibraryAction

    /** Delete the document currently awaiting confirmation. */
    public data object DeleteConfirmed : DocumentLibraryAction

    /** Keep the document currently awaiting confirmation. */
    public data object DeleteDismissed : DocumentLibraryAction

    /** Clear transient feedback. */
    public data object NoticeDismissed : DocumentLibraryAction
}
