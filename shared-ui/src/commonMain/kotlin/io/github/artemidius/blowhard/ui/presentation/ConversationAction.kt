package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId

/** User intent accepted by [ConversationPresenter]. */
public sealed interface ConversationAction {
    /** Replaces the composer contents with [value]. */
    public data class ComposerChanged(public val value: String) : ConversationAction

    /** Selects an existing conversation. */
    public data class ConversationSelected(public val id: ConversationId) : ConversationAction

    /** Selects the offline behavior prepared for the next run. */
    public data class ScenarioChanged(public val scenario: DemoScenario) : ConversationAction

    /** Creates and selects a local in-memory conversation. */
    public data object NewConversation : ConversationAction

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
