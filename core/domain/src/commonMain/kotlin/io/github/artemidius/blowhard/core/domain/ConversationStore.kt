package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId

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
 * Port for durable local conversation metadata.
 *
 * Implementations serialize operations, preserve an existing creation timestamp on update, and
 * reject operations after [close]. Credentials and provider secrets are outside this boundary.
 */
public interface ConversationStore {
    /** Inserts [conversation] or updates its mutable metadata. */
    public suspend fun save(conversation: StoredConversation)

    /** Returns the conversation matching [id], or `null` when it is absent. */
    public suspend fun find(id: ConversationId): StoredConversation?

    /** Lists conversations by most recent update, then stable identity. */
    public suspend fun list(): List<StoredConversation>

    /** Deletes [id] and reports whether a row existed. */
    public suspend fun delete(id: ConversationId): Boolean

    /** Releases database resources. Repeated calls have no effect. */
    public suspend fun close()
}
