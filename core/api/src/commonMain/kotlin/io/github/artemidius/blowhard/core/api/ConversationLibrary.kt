package io.github.artemidius.blowhard.core.api

/** Client-facing lifecycle operations for locally owned conversation history. */
public interface ConversationLibrary {
    /**
     * Deletes the live local conversation aggregate identified by [conversationId].
     *
     * Deletion never contacts a generation provider and never changes provider-side retention.
     */
    public suspend fun delete(conversationId: ConversationId): ConversationDeletionResult
}

/** Safe outcome of a requested local conversation deletion. */
public enum class ConversationDeletionResult {
    /** The durable conversation and all of its owned rows were deleted. */
    DELETED,

    /** No durable conversation matched the requested identity. */
    NOT_FOUND,

    /** The conversation owns active work and must be stopped before deletion. */
    CONVERSATION_BUSY,

    /** Durable storage could not complete the deletion safely. */
    STORAGE_UNAVAILABLE,

    /** The owning application lifecycle has already closed. */
    LIFECYCLE_CLOSED,
}
