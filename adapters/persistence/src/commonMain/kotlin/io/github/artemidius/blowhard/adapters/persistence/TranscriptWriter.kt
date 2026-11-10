package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.adapters.persistence.db.TranscriptQueries
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredTranscript

internal fun TranscriptQueries.replace(transcript: StoredTranscript) {
    val conversationId = transcript.conversationId.value
    deleteOwnedRows(transcript.conversationId)
    transcript.messages.sortedBy { it.sequence }.forEach { message ->
        insertMessage(
            id = message.id.value,
            conversation_id = conversationId,
            sequence_number = message.sequence,
            role = message.role.name,
            text_content = message.text,
            created_at_epoch_ms = message.createdAtEpochMilliseconds,
        ).value
    }
    transcript.attempts
        .sortedWith(compareBy(StoredGenerationAttempt::createdAtEpochMilliseconds, { it.runId.value }))
        .forEach { attempt -> insertAttempt(conversationId, attempt) }
}

internal fun TranscriptQueries.deleteOwnedRows(conversationId: ConversationId) {
    deleteAttempts(conversationId.value).value
    deleteMessages(conversationId.value).value
}

private fun TranscriptQueries.insertAttempt(conversationId: String, attempt: StoredGenerationAttempt) {
    val target = attempt.target.toColumns()
    val failure = attempt.failure
    insertAttempt(
        id = attempt.runId.value,
        conversation_id = conversationId,
        user_message_id = attempt.userMessageId.value,
        assistant_message_id = attempt.assistantMessageId.value,
        source = target.source,
        model_id = target.modelId,
        max_output_tokens = attempt.parameters.maxOutputTokens?.toLong(),
        temperature = attempt.parameters.temperature,
        status = attempt.status.name,
        failure_kind = failure?.kind?.name,
        failure_reference = failure?.reference,
        retry_advice = failure?.retryAdvice?.name,
        retry_after_seconds = failure?.retryAfterSeconds,
        created_at_epoch_ms = attempt.createdAtEpochMilliseconds,
        updated_at_epoch_ms = attempt.updatedAtEpochMilliseconds,
    ).value
}

private fun GenerationTarget.toColumns(): TargetColumns = when (this) {
    GenerationTarget.Demo -> TargetColumns(source = "DEMO", modelId = null)
    is GenerationTarget.Live -> TargetColumns(source = "LIVE", modelId = modelId)
}

private data class TargetColumns(val source: String, val modelId: String?)
