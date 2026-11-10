package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunTimestamp

/** Durable, non-secret metadata for one locally owned conversation. */
public data class StoredConversation(
    public val id: ConversationId,
    public val title: String,
    public val createdAtEpochMilliseconds: Long,
    public val updatedAtEpochMilliseconds: Long,
) {
    init {
        require(title.isNotBlank()) { "Conversation title must not be blank" }
        require(title.length <= MAX_TITLE_LENGTH) { "Conversation title is too long" }
        require(createdAtEpochMilliseconds >= 0) { "Creation time must not be negative" }
        require(updatedAtEpochMilliseconds >= createdAtEpochMilliseconds) {
            "Update time must not precede creation time"
        }
    }

    /** Shared validation bounds for stored conversation metadata. */
    public companion object {
        /** Maximum number of Kotlin characters accepted for a persisted title. */
        public const val MAX_TITLE_LENGTH: Int = 200
    }
}

/**
 * Port for durable local conversations and their transcript snapshots.
 *
 * Implementations serialize operations, preserve an existing creation timestamp on update, and
 * replace each transcript atomically. Credentials and provider secrets are outside this boundary.
 * Operations after [close] are rejected.
 */
public interface ConversationStore {
    /** Inserts [conversation] or updates its mutable metadata. */
    public suspend fun save(conversation: StoredConversation)

    /** Returns the conversation matching [id], or `null` when it is absent. */
    public suspend fun find(id: ConversationId): StoredConversation?

    /** Lists conversations by most recent update, then stable identity. */
    public suspend fun list(): List<StoredConversation>

    /**
     * Atomically saves [conversation] and replaces its complete [transcript].
     *
     * The identities must match. Implementations preserve an existing conversation creation
     * timestamp and leave both metadata and transcript unchanged when any part of the checkpoint
     * fails.
     */
    public suspend fun saveCheckpoint(conversation: StoredConversation, transcript: StoredTranscript)

    /** Atomically replaces the messages and attempts owned by an existing conversation. */
    public suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult

    /** Returns the transcript owned by [id], or `null` when the conversation is absent. */
    public suspend fun loadTranscript(id: ConversationId): StoredTranscript?

    /**
     * Marks every queued or running attempt as interrupted after a process restart.
     *
     * This operation is atomic and idempotent. It preserves message content and immutable attempt
     * snapshots, and it never starts or replays provider work.
     */
    public suspend fun recoverInFlightAttempts(recoveredAt: RunTimestamp): ConversationRecoveryResult

    /** Deletes [id] and reports whether a row existed. */
    public suspend fun delete(id: ConversationId): Boolean

    /** Releases database resources. Repeated calls have no effect. */
    public suspend fun close()
}

/** Result of atomically replacing one durable transcript. */
public enum class TranscriptSaveResult {
    /** The complete transcript replaced its previous durable value. */
    SAVED,

    /** No transcript was written because its conversation metadata was absent. */
    CONVERSATION_NOT_FOUND,
}

/** Summary of one fail-closed restart recovery pass. */
public data class ConversationRecoveryResult(public val interruptedAttemptCount: Long) {
    init {
        require(interruptedAttemptCount >= 0) { "Interrupted attempt count must not be negative" }
    }
}
