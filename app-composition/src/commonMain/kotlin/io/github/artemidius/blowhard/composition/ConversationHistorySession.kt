package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.application.ConversationHistoryLoader
import io.github.artemidius.blowhard.core.application.RunClock
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns one durable conversation store and its fail-closed startup recovery result. */
public class ConversationHistorySession private constructor(
    public val initialState: ConversationUiState,
    public val available: Boolean,
    public val recoveredAttemptCount: Long,
    public val conversationStore: ConversationStore?,
) {
    private val closeAccess = Mutex()
    private var closed = false

    /** Closes the owned store at most once. Storage close failures do not escape shutdown. */
    public suspend fun close() {
        closeAccess.withLock {
            if (closed) return
            closed = true
            conversationStore.closeSafely()
        }
    }

    /** Creates recovered or explicitly unavailable history sessions. */
    public companion object {
        /** Opens and recovers a store, or returns an explicit unavailable session on any storage failure. */
        public suspend fun open(
            clock: RunClock = SystemRunClock,
            openStore: () -> ConversationStore,
        ): ConversationHistorySession {
            var store: ConversationStore? = null
            return try {
                val openedStore = openStore()
                store = openedStore
                val snapshot = ConversationHistoryLoader(openedStore, clock).load()
                ConversationHistorySession(
                    initialState = snapshot.toConversationUiState(),
                    available = true,
                    recoveredAttemptCount = snapshot.recoveredAttemptCount,
                    conversationStore = openedStore,
                )
            } catch (_: Exception) {
                store.closeSafely()
                unavailable()
            }
        }

        /** Creates a provider-inert session when durable storage cannot be opened. */
        public fun unavailable(): ConversationHistorySession = ConversationHistorySession(
            initialState = unavailableConversationHistoryState(),
            available = false,
            recoveredAttemptCount = 0,
            conversationStore = null,
        )
    }
}

private suspend fun ConversationStore?.closeSafely() {
    val store = this ?: return
    try {
        store.close()
    } catch (_: Exception) {
        // Startup and shutdown remain fail closed even when the storage driver cannot close cleanly.
    }
}
