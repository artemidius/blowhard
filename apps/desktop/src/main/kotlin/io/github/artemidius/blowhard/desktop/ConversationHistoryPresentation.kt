package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.application.ConversationHistoryEntry
import io.github.artemidius.blowhard.core.application.ConversationHistorySnapshot
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.ui.presentation.ChatMessageUi
import io.github.artemidius.blowhard.ui.presentation.ChatRole
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import io.github.artemidius.blowhard.ui.presentation.ConversationUiModel
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.MessageDelivery
import io.github.artemidius.blowhard.ui.presentation.UiNotice

internal fun ConversationHistorySnapshot.toConversationUiState(): ConversationUiState {
    if (entries.isEmpty()) return emptyDesktopConversationState()
    val conversations = entries.map(ConversationHistoryEntry::toConversationUiModel)
    return ConversationUiState(
        conversations = conversations,
        selectedConversationId = conversations.first().id,
    )
}

internal fun unavailableHistoryState(): ConversationUiState = emptyDesktopConversationState(
    UiNotice("Local conversation history is unavailable. This session is not connected to durable storage."),
)

internal fun emptyDesktopConversationState(notice: UiNotice? = null): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id, "New conversation", notice = notice)),
        selectedConversationId = id,
    )
}

private fun ConversationHistoryEntry.toConversationUiModel(): ConversationUiModel {
    val attemptsByMessage = buildMap<MessageId, StoredGenerationAttempt> {
        transcript.attempts.forEach { attempt ->
            put(attempt.userMessageId, attempt)
            put(attempt.assistantMessageId, attempt)
        }
    }
    val latestAttempt = transcript.attempts.maxWithOrNull(
        compareBy(StoredGenerationAttempt::createdAtEpochMilliseconds, { it.runId.value }),
    )
    return ConversationUiModel(
        id = conversation.id,
        title = conversation.title,
        messages = transcript.messages.sortedBy { it.sequence }.map { message ->
            val attempt = attemptsByMessage[message.id]
            ChatMessageUi(
                id = message.id.value,
                role = message.role.toChatRole(),
                text = message.text,
                delivery = message.delivery(attempt),
                target = attempt?.target,
                attemptId = attempt?.runId,
                canRegenerate = message.role == StoredMessageRole.ASSISTANT && attempt.canRegenerate(),
            )
        },
        runState = latestAttempt.toConversationRunState(),
        notice = latestAttempt.interruptionNotice(),
        latestTarget = latestAttempt?.target,
    )
}

private fun StoredMessageRole.toChatRole(): ChatRole = when (this) {
    StoredMessageRole.USER -> ChatRole.USER
    StoredMessageRole.ASSISTANT -> ChatRole.ASSISTANT
}

private fun io.github.artemidius.blowhard.core.domain.StoredMessage.delivery(
    attempt: StoredGenerationAttempt?,
): MessageDelivery = if (role == StoredMessageRole.USER) {
    MessageDelivery.COMPLETE
} else {
    when (attempt?.status) {
        null, StoredAttemptStatus.SUCCEEDED -> MessageDelivery.COMPLETE

        StoredAttemptStatus.FAILED -> MessageDelivery.FAILED

        StoredAttemptStatus.CANCELLED -> MessageDelivery.CANCELLED

        StoredAttemptStatus.QUEUED,
        StoredAttemptStatus.RUNNING,
        StoredAttemptStatus.INTERRUPTED,
        -> MessageDelivery.INTERRUPTED
    }
}

private fun StoredGenerationAttempt?.toConversationRunState(): ConversationRunState = when (this?.status) {
    null -> ConversationRunState.IDLE

    StoredAttemptStatus.SUCCEEDED -> ConversationRunState.SUCCEEDED

    StoredAttemptStatus.FAILED -> ConversationRunState.FAILED

    StoredAttemptStatus.CANCELLED -> ConversationRunState.CANCELLED

    StoredAttemptStatus.QUEUED,
    StoredAttemptStatus.RUNNING,
    StoredAttemptStatus.INTERRUPTED,
    -> ConversationRunState.INTERRUPTED
}

private fun StoredGenerationAttempt?.canRegenerate(): Boolean = when (this?.status) {
    null -> false

    StoredAttemptStatus.FAILED -> failure?.retryAdvice == RunRetryAdvice.EXPLICIT_NEW_ATTEMPT

    StoredAttemptStatus.SUCCEEDED,
    StoredAttemptStatus.CANCELLED,
    StoredAttemptStatus.INTERRUPTED,
    -> true

    StoredAttemptStatus.QUEUED, StoredAttemptStatus.RUNNING -> false
}

private fun StoredGenerationAttempt?.interruptionNotice(): UiNotice? =
    if (this?.status == StoredAttemptStatus.INTERRUPTED) {
        UiNotice("The previous process stopped before this attempt reached a known outcome. It was not replayed.")
    } else {
        null
    }
