package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ConversationLibrary
import kotlinx.coroutines.CancellationException

internal class ConversationManagementPresenter(
    private val library: ConversationLibrary,
    private val conversationIds: PresentationIdSequence,
) {
    suspend fun handle(state: ConversationUiState, action: ConversationAction): ConversationUiState = when (action) {
        ConversationAction.NewConversation -> state.createConversation()
        is ConversationAction.DeleteConversationRequested -> state.requestDeletion(action.id)
        ConversationAction.DeleteConversationConfirmed -> confirmDeletion(state)
        ConversationAction.DeleteConversationDismissed -> state.copy(pendingDeletionConversationId = null)
        else -> error("Unsupported conversation management action")
    }

    private fun ConversationUiState.createConversation(): ConversationUiState {
        val conversation = newConversation()
        return copy(
            conversations = conversations + conversation,
            selectedConversationId = conversation.id,
            composer = "",
        )
    }

    private fun ConversationUiState.requestDeletion(id: ConversationId): ConversationUiState {
        val conversation = conversations.firstOrNull { it.id == id } ?: return this
        return if (conversation.runState.isActive) {
            updateConversation(id) { it.copy(notice = UiNotice(ACTIVE_DELETION_MESSAGE)) }
        } else {
            copy(pendingDeletionConversationId = id)
        }
    }

    private suspend fun confirmDeletion(state: ConversationUiState): ConversationUiState {
        val conversationId = state.pendingDeletionConversationId ?: return state
        val result = try {
            library.delete(conversationId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        return when (result) {
            ConversationDeletionResult.DELETED, ConversationDeletionResult.NOT_FOUND ->
                state.removeConversation(conversationId, ::newConversation)
                    .withSelectedNotice(UiNotice(DELETION_SUCCESS_MESSAGE))

            ConversationDeletionResult.CONVERSATION_BUSY -> state.deletionFailed(ACTIVE_DELETION_MESSAGE)

            ConversationDeletionResult.STORAGE_UNAVAILABLE, null ->
                state.deletionFailed(DELETION_UNAVAILABLE_MESSAGE)

            ConversationDeletionResult.LIFECYCLE_CLOSED -> state.deletionFailed(DELETION_CLOSED_MESSAGE)
        }
    }

    private fun newConversation(): ConversationUiModel = ConversationUiModel(
        id = ConversationId(conversationIds.next()),
        title = "New conversation",
    )
}

private const val ACTIVE_DELETION_MESSAGE = "Stop the active run before deleting this conversation."
private const val DELETION_SUCCESS_MESSAGE = "The conversation was removed from local history."
private const val DELETION_UNAVAILABLE_MESSAGE =
    "Local history deletion could not be confirmed. The conversation remains visible."
private const val DELETION_CLOSED_MESSAGE = "The application is shutting down and cannot delete local history."
