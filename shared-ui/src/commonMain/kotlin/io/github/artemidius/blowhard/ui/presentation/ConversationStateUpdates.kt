package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget

internal fun initialConversationState(): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id = id, title = "Demo conversation")),
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

internal fun ConversationUiState.beginRun(
    conversationId: ConversationId,
    prompt: String,
    userMessageId: String,
    assistantMessageId: String,
    target: GenerationTarget,
): ConversationUiState = updateConversation(conversationId) { conversation ->
    val title = if (conversation.messages.isEmpty()) prompt.toConversationTitle() else conversation.title
    conversation.copy(
        title = title,
        runState = ConversationRunState.QUEUED,
        notice = null,
        latestTarget = target,
        messages = conversation.messages + listOf(
            ChatMessageUi(userMessageId, ChatRole.USER, prompt, target = target),
            ChatMessageUi(
                id = assistantMessageId,
                role = ChatRole.ASSISTANT,
                text = "",
                delivery = MessageDelivery.STREAMING,
                target = target,
            ),
        ),
    )
}.copy(composer = "")

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
                message.copy(text = snapshot.output, delivery = snapshot.delivery)
            } else {
                message
            }
        },
    )
}

internal fun ConversationUiState.withSelectedNotice(notice: UiNotice?): ConversationUiState =
    updateConversation(selectedConversationId) { it.copy(notice = notice) }

private fun String.toConversationTitle(): String {
    val firstLine = lineSequence().firstOrNull().orEmpty().trim()
    return if (firstLine.length <= TITLE_LIMIT) firstLine else firstLine.take(TITLE_LIMIT) + "…"
}

private const val TITLE_LIMIT = 36
