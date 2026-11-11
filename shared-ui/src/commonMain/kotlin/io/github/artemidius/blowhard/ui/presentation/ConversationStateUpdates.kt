package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId

internal fun initialConversationState(): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id = id, title = "New conversation")),
        selectedConversationId = id,
    )
}

internal fun ConversationUiState.updateConversation(
    id: ConversationId,
    transform: (ConversationUiModel) -> ConversationUiModel,
): ConversationUiState = copy(
    conversations = conversations.map { conversation ->
        if (conversation.id == id) transform(conversation) else conversation
    },
)

internal fun ConversationUiState.removeConversation(
    id: ConversationId,
    replacement: () -> ConversationUiModel,
): ConversationUiState {
    val removedIndex = conversations.indexOfFirst { it.id == id }
    val remaining = conversations.filterNot { it.id == id }
    return when {
        removedIndex < 0 -> copy(pendingDeletionConversationId = null)

        remaining.isEmpty() -> {
            val fresh = replacement()
            copy(
                conversations = listOf(fresh),
                selectedConversationId = fresh.id,
                composer = "",
                pendingDeletionConversationId = null,
            )
        }

        else -> copy(
            conversations = remaining,
            selectedConversationId = if (selectedConversationId == id) {
                remaining[minOf(removedIndex, remaining.lastIndex)].id
            } else {
                selectedConversationId
            },
            composer = if (selectedConversationId == id) "" else composer,
            pendingDeletionConversationId = null,
        )
    }
}

internal fun ConversationUiState.deletionFailed(message: String): ConversationUiState =
    copy(pendingDeletionConversationId = null).withSelectedNotice(UiNotice(message))

internal fun ConversationUiState.prepareRunStart(
    conversationId: ConversationId,
    clearComposer: Boolean,
): ConversationUiState = updateConversation(conversationId) {
    it.copy(runState = ConversationRunState.STARTING, notice = null)
}.let { state ->
    if (clearComposer) state.copy(composer = "", selectedImage = null) else state
}

internal fun ConversationUiState.regenerationPrompt(messageId: String): String? {
    val conversation = selectedConversation
    return if (conversation.runState.isActive) {
        null
    } else {
        conversation.messages.firstOrNull { message ->
            message.id == messageId &&
                message.role == ChatRole.ASSISTANT &&
                message.canRegenerate &&
                message.attemptId != null
        }?.let { response ->
            conversation.messages.firstOrNull { message ->
                message.role == ChatRole.USER && message.attemptId == response.attemptId
            }?.text
        }
    }
}

internal fun ConversationUiState.beginRun(
    conversationId: ConversationId,
    runId: RunId,
    prompt: String,
    userMessageId: String,
    assistantMessageId: String,
    target: GenerationTarget,
    attachedImageName: String?,
): ConversationUiState = updateConversation(conversationId) { conversation ->
    val title = if (conversation.messages.isEmpty()) prompt.toConversationTitle() else conversation.title
    conversation.copy(
        title = title,
        runState = ConversationRunState.QUEUED,
        notice = null,
        latestTarget = target,
        messages = conversation.messages + listOf(
            ChatMessageUi(
                id = userMessageId,
                role = ChatRole.USER,
                text = prompt,
                target = target,
                attemptId = runId,
                attachedImageName = attachedImageName,
            ),
            ChatMessageUi(
                id = assistantMessageId,
                role = ChatRole.ASSISTANT,
                text = "",
                delivery = MessageDelivery.STREAMING,
                target = target,
                attemptId = runId,
            ),
        ),
    )
}

internal fun ConversationUiState.applySnapshot(
    conversationId: ConversationId,
    assistantMessageId: String,
    snapshot: PresentedSnapshot,
): ConversationUiState = updateConversation(conversationId) { conversation ->
    conversation.copy(
        runState = snapshot.runState,
        notice = snapshot.notice ?: conversation.notice,
        messages = conversation.messages.map { message ->
            if (message.id == assistantMessageId) {
                message.copy(
                    text = snapshot.output,
                    delivery = snapshot.delivery,
                    canRegenerate = snapshot.canRegenerate,
                )
            } else {
                message
            }
        },
    )
}

internal fun ConversationUiState.withSelectedNotice(notice: UiNotice?): ConversationUiState =
    updateConversation(selectedConversationId) { it.copy(notice = notice) }

private fun String.toConversationTitle(): String {
    val firstContentLine = lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
    val title = firstContentLine.ifEmpty { DEFAULT_CONVERSATION_TITLE }
    return if (title.length <= TITLE_LIMIT) title else title.take(TITLE_LIMIT) + "…"
}

private const val TITLE_LIMIT = 36
private const val DEFAULT_CONVERSATION_TITLE = "Conversation"
