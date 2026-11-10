package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript

internal class ConversationRunCheckpoints(
    private val store: ConversationStore,
    private val policy: RunCheckpointPolicy,
) {
    suspend fun begin(record: RunRecord) {
        val checkpoint = initialCheckpoint(record)
        store.saveCheckpoint(checkpoint.conversation, checkpoint.transcript)
        record.durableCheckpoint = checkpoint
    }

    suspend fun update(record: RunRecord, snapshot: RunSnapshot) {
        val current = checkNotNull(record.durableCheckpoint) { "Durable run was not initialized" }
        if (!current.shouldPersist(snapshot, policy)) return

        val updated = current.updated(snapshot)
        store.saveCheckpoint(updated.conversation, updated.transcript)
        record.durableCheckpoint = updated
    }

    private suspend fun initialCheckpoint(record: RunRecord): DurableRunCheckpoint {
        val timestamp = record.snapshots.value.updatedAt.epochMilliseconds
        val existingConversation = store.find(record.conversationId)
        val existingTranscript = store.loadTranscript(record.conversationId)
            ?: StoredTranscript(record.conversationId, emptyList(), emptyList())
        val messageIds = DurableMessageIds(
            user = MessageId("${record.runId.value}:user"),
            assistant = MessageId("${record.runId.value}:assistant"),
        )
        return DurableRunCheckpoint(
            conversation = record.toStoredConversation(existingConversation, timestamp),
            transcript = existingTranscript.append(record, messageIds, timestamp),
            messageIds = messageIds,
        )
    }
}

internal data class DurableRunCheckpoint(
    val conversation: StoredConversation,
    val transcript: StoredTranscript,
    val messageIds: DurableMessageIds,
) {
    fun shouldPersist(snapshot: RunSnapshot, policy: RunCheckpointPolicy): Boolean {
        val attempt = transcript.attempts.first { it.runId == snapshot.runId }
        return if (snapshot is RunSnapshot.Running && attempt.status == StoredAttemptStatus.RUNNING) {
            val persistedOutput = transcript.messages.first { it.id == messageIds.assistant }.text
            val outputGrowth = snapshot.output.length - persistedOutput.length
            val elapsed = snapshot.updatedAt.epochMilliseconds - attempt.updatedAtEpochMilliseconds
            outputGrowth >= policy.minimumOutputCharacters || elapsed >= policy.maximumIntervalMilliseconds
        } else {
            true
        }
    }

    fun updated(snapshot: RunSnapshot): DurableRunCheckpoint {
        val attemptIndex = transcript.attempts.indexOfFirst { it.runId == snapshot.runId }
        check(attemptIndex >= 0) { "Durable attempt is missing" }
        val previousAttempt = transcript.attempts[attemptIndex]
        val updatedAt = maxOf(
            previousAttempt.createdAtEpochMilliseconds,
            previousAttempt.updatedAtEpochMilliseconds,
            snapshot.updatedAt.epochMilliseconds,
        )
        val updatedAttempt = previousAttempt.copy(
            status = snapshot.toStoredStatus(),
            failure = (snapshot as? RunSnapshot.Failed)?.failure,
            updatedAtEpochMilliseconds = updatedAt,
        )
        val updatedTranscript = transcript.copy(
            messages = transcript.messages.map { message ->
                if (message.id == messageIds.assistant) message.copy(text = snapshot.output()) else message
            },
            attempts = transcript.attempts.toMutableList().apply { this[attemptIndex] = updatedAttempt },
        )
        return copy(
            conversation = conversation.copy(
                updatedAtEpochMilliseconds = maxOf(conversation.updatedAtEpochMilliseconds, updatedAt),
            ),
            transcript = updatedTranscript,
        )
    }
}

internal data class DurableMessageIds(val user: MessageId, val assistant: MessageId)

private fun RunRecord.toStoredConversation(existing: StoredConversation?, timestamp: Long): StoredConversation =
    existing?.copy(
        updatedAtEpochMilliseconds = maxOf(existing.updatedAtEpochMilliseconds, timestamp),
    ) ?: StoredConversation(
        id = conversationId,
        title = prompt.value.toConversationTitle(),
        createdAtEpochMilliseconds = timestamp,
        updatedAtEpochMilliseconds = timestamp,
    )

private fun StoredTranscript.append(
    record: RunRecord,
    messageIds: DurableMessageIds,
    timestamp: Long,
): StoredTranscript {
    val firstSequence = nextMessageSequence()
    return copy(
        messages = messages + record.storedMessages(messageIds, firstSequence, timestamp),
        attempts = attempts + record.storedAttempt(messageIds, timestamp),
    )
}

private fun RunRecord.storedMessages(
    messageIds: DurableMessageIds,
    firstSequence: Long,
    timestamp: Long,
): List<StoredMessage> = listOf(
    StoredMessage(messageIds.user, firstSequence, StoredMessageRole.USER, prompt.value, timestamp),
    StoredMessage(messageIds.assistant, firstSequence + 1, StoredMessageRole.ASSISTANT, "", timestamp),
)

private fun RunRecord.storedAttempt(messageIds: DurableMessageIds, timestamp: Long): StoredGenerationAttempt =
    StoredGenerationAttempt(
        runId = runId,
        userMessageId = messageIds.user,
        assistantMessageId = messageIds.assistant,
        target = target,
        parameters = parameters,
        status = StoredAttemptStatus.QUEUED,
        createdAtEpochMilliseconds = timestamp,
        updatedAtEpochMilliseconds = timestamp,
    )

private fun StoredTranscript.nextMessageSequence(): Long {
    val latest = messages.maxOfOrNull(StoredMessage::sequence) ?: -1
    check(latest <= Long.MAX_VALUE - 2) { "Conversation message sequence is exhausted" }
    return latest + 1
}

private fun RunSnapshot.toStoredStatus(): StoredAttemptStatus = when (this) {
    is RunSnapshot.Queued -> StoredAttemptStatus.QUEUED
    is RunSnapshot.Running -> StoredAttemptStatus.RUNNING
    is RunSnapshot.Succeeded -> StoredAttemptStatus.SUCCEEDED
    is RunSnapshot.Failed -> StoredAttemptStatus.FAILED
    is RunSnapshot.Cancelled -> StoredAttemptStatus.CANCELLED
}

private fun String.toConversationTitle(): String {
    val firstContentLine = lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
    val title = firstContentLine.ifEmpty { DEFAULT_CONVERSATION_TITLE }
    return if (title.length <= TITLE_LIMIT) title else title.take(TITLE_LIMIT) + "…"
}

private const val TITLE_LIMIT = 36
private const val DEFAULT_CONVERSATION_TITLE = "Conversation"
