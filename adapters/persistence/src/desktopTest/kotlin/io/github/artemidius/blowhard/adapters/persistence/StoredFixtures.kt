package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript

internal object StoredFixtures {
    fun conversation(id: String, title: String, createdAt: Long, updatedAt: Long): StoredConversation =
        StoredConversation(ConversationId(id), title, createdAt, updatedAt)

    fun message(id: String, sequence: Long, role: StoredMessageRole, text: String): StoredMessage =
        StoredMessage(MessageId(id), sequence, role, text, sequence + 1)

    fun attempt(
        runId: String,
        userMessageId: String,
        assistantMessageId: String,
        target: GenerationTarget = GenerationTarget.Demo,
        parameters: GenerationParameters = GenerationParameters(),
        status: StoredAttemptStatus = StoredAttemptStatus.SUCCEEDED,
        failure: RunFailure? = null,
        createdAt: Long = 1,
        updatedAt: Long = createdAt,
    ): StoredGenerationAttempt = StoredGenerationAttempt(
        runId = RunId(runId),
        userMessageId = MessageId(userMessageId),
        assistantMessageId = MessageId(assistantMessageId),
        target = target,
        parameters = parameters,
        status = status,
        failure = failure,
        createdAtEpochMilliseconds = createdAt,
        updatedAtEpochMilliseconds = updatedAt,
    )

    fun transcript(
        conversationId: String,
        messages: List<StoredMessage>,
        attempts: List<StoredGenerationAttempt>,
    ): StoredTranscript = StoredTranscript(ConversationId(conversationId), messages, attempts)
}
