package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.persistence.DesktopConversationStores
import io.github.artemidius.blowhard.composition.toConversationUiState
import io.github.artemidius.blowhard.composition.unavailableConversationHistoryState
import io.github.artemidius.blowhard.core.application.ConversationHistoryLoader
import io.github.artemidius.blowhard.core.application.RunClock
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopHistorySession private constructor(
    val initialState: ConversationUiState,
    val available: Boolean,
    val recoveredAttemptCount: Long,
    val conversationStore: ConversationStore?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        conversationStore.closeSafely()
    }

    companion object {
        fun open(
            configurationDirectory: Path,
            clock: RunClock = SystemRunClock,
            openStore: (String) -> ConversationStore = DesktopConversationStores::open,
        ): DesktopHistorySession {
            var store: ConversationStore? = null
            return try {
                Files.createDirectories(configurationDirectory)
                val openedStore = openStore(conversationDatabasePath(configurationDirectory).toString())
                store = openedStore
                val snapshot = runBlocking { ConversationHistoryLoader(openedStore, clock).load() }
                DesktopHistorySession(
                    initialState = snapshot.toConversationUiState(),
                    available = true,
                    recoveredAttemptCount = snapshot.recoveredAttemptCount,
                    conversationStore = openedStore,
                )
            } catch (_: Exception) {
                store.closeSafely()
                DesktopHistorySession(
                    initialState = unavailableConversationHistoryState(),
                    available = false,
                    recoveredAttemptCount = 0,
                    conversationStore = null,
                )
            }
        }
    }
}

internal fun conversationDatabasePath(configurationDirectory: Path): Path =
    configurationDirectory.resolve(CONVERSATION_DATABASE_FILE_NAME)

private fun ConversationStore?.closeSafely() {
    val ownedStore = this ?: return
    try {
        runBlocking { ownedStore.close() }
    } catch (_: Exception) {
        // The unavailable state remains safe even when a partially opened store cannot close cleanly.
    }
}

private const val CONVERSATION_DATABASE_FILE_NAME = "conversations.db"
