package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredTranscript

/** One durable conversation and its matching transcript, ready for client presentation. */
public data class ConversationHistoryEntry(
    public val conversation: StoredConversation,
    public val transcript: StoredTranscript,
) {
    init {
        require(conversation.id == transcript.conversationId) {
            "Conversation history identities must match"
        }
    }
}

/** Complete fail-closed startup view of local conversation history. */
public data class ConversationHistorySnapshot(
    public val entries: List<ConversationHistoryEntry>,
    public val recoveredAttemptCount: Long,
) {
    init {
        require(recoveredAttemptCount >= 0) { "Recovered attempt count must not be negative" }
        require(entries.map { it.conversation.id }.toSet().size == entries.size) {
            "Conversation history identities must be unique"
        }
    }
}

/**
 * Recovers uncertain attempts before loading durable conversation history.
 *
 * Loading never invokes a generation provider. Storage failures propagate to the composition root,
 * which decides whether and how to expose an unavailable-history state.
 */
public class ConversationHistoryLoader(private val store: ConversationStore, private val clock: RunClock) {
    /** Marks in-flight attempts interrupted, then returns conversations in store-defined order. */
    public suspend fun load(): ConversationHistorySnapshot {
        val recovery = store.recoverInFlightAttempts(clock.now())
        val entries = store.list().map { conversation ->
            val transcript = checkNotNull(store.loadTranscript(conversation.id)) {
                "Conversation disappeared while startup history was loading"
            }
            ConversationHistoryEntry(conversation, transcript)
        }
        return ConversationHistorySnapshot(entries, recovery.interruptedAttemptCount)
    }
}
