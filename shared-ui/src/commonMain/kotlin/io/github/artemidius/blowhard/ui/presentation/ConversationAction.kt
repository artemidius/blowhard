package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.DocumentId

/** User intent accepted by [ConversationPresenter]. */
public sealed interface ConversationAction {
    /** Replaces the composer contents with [value]. */
    public data class ComposerChanged(public val value: String) : ConversationAction

    /** Selects an existing conversation. */
    public data class ConversationSelected(public val id: ConversationId) : ConversationAction

    /** Selects the offline behavior prepared for the next run. */
    public data class ScenarioChanged(public val scenario: DemoScenario) : ConversationAction

    /** Attaches one explicitly selected, already imported image to the next attempt. */
    public data class ImageSelected(public val id: DocumentId, public val displayName: String) : ConversationAction

    /** Removes the image selected for the next attempt. */
    public data object ImageCleared : ConversationAction

    /** Creates and selects a local in-memory conversation. */
    public data object NewConversation : ConversationAction

    /** Requests confirmation before deleting the local conversation identified by [id]. */
    public data class DeleteConversationRequested(public val id: ConversationId) : ConversationAction

    /** Confirms deletion of the conversation currently named by the confirmation state. */
    public data object DeleteConversationConfirmed : ConversationAction

    /** Closes the local-deletion confirmation without changing conversation history. */
    public data object DeleteConversationDismissed : ConversationAction

    /** Starts generation from the current composer contents. */
    public data object Send : ConversationAction

    /** Requests cancellation of the selected conversation's active run. */
    public data object Stop : ConversationAction

    /** Starts a distinct attempt for the prompt associated with assistant [messageId]. */
    public data class Regenerate(public val messageId: String) : ConversationAction

    /** Copies one visible message without exposing platform clipboard types. */
    public data class CopyMessage(public val messageId: String) : ConversationAction

    /** Removes the current non-sensitive feedback notice. */
    public data object NoticeDismissed : ConversationAction
}
