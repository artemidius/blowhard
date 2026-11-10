package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.adapters.persistence.db.Generation_attempt
import io.github.artemidius.blowhard.adapters.persistence.db.Message
import io.github.artemidius.blowhard.adapters.persistence.db.TranscriptQueries
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript

internal fun TranscriptQueries.load(conversationId: ConversationId): StoredTranscript = StoredTranscript(
    conversationId = conversationId,
    messages = listMessages(conversationId.value).executeAsList().map(Message::toStoredMessage),
    attempts = listAttempts(conversationId.value).executeAsList().map(Generation_attempt::toStoredAttempt),
)

private fun Message.toStoredMessage(): StoredMessage = StoredMessage(
    id = MessageId(id),
    sequence = sequence_number,
    role = StoredMessageRole.valueOf(role),
    text = text_content,
    createdAtEpochMilliseconds = created_at_epoch_ms,
)

private fun Generation_attempt.toStoredAttempt(): StoredGenerationAttempt = StoredGenerationAttempt(
    runId = RunId(id),
    userMessageId = MessageId(user_message_id),
    assistantMessageId = MessageId(assistant_message_id),
    target = toGenerationTarget(),
    parameters = GenerationParameters(max_output_tokens?.toInt(), temperature),
    status = StoredAttemptStatus.valueOf(status),
    failure = toRunFailure(),
    createdAtEpochMilliseconds = created_at_epoch_ms,
    updatedAtEpochMilliseconds = updated_at_epoch_ms,
)

private fun Generation_attempt.toGenerationTarget(): GenerationTarget = when (source) {
    "DEMO" -> GenerationTarget.Demo
    "LIVE" -> GenerationTarget.Live(requireNotNull(model_id))
    else -> error("Unsupported stored generation source")
}

private fun Generation_attempt.toRunFailure(): RunFailure? = failure_kind?.let { kind ->
    RunFailure(
        kind = RunFailureKind.valueOf(kind),
        reference = failure_reference,
        retryAdvice = RunRetryAdvice.valueOf(requireNotNull(retry_advice)),
        retryAfterSeconds = retry_after_seconds,
    )
}
