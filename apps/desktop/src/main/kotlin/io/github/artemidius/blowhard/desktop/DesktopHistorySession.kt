package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.persistence.DesktopConversationStores
import io.github.artemidius.blowhard.composition.ConversationHistorySession
import io.github.artemidius.blowhard.core.application.RunClock
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

internal class DesktopHistorySession private constructor(private val session: ConversationHistorySession) :
    AutoCloseable {
    val initialState: ConversationUiState
        get() = session.initialState
    val available: Boolean
        get() = session.available
    val recoveredAttemptCount: Long
        get() = session.recoveredAttemptCount
    val conversationStore: ConversationStore?
        get() = session.conversationStore

    override fun close() {
        runBlocking { session.close() }
    }

    companion object {
        fun open(
            configurationDirectory: Path,
            clock: RunClock = SystemRunClock,
            openStore: (String) -> ConversationStore = DesktopConversationStores::open,
        ): DesktopHistorySession = DesktopHistorySession(
            runBlocking {
                ConversationHistorySession.open(clock) {
                    Files.createDirectories(configurationDirectory)
                    openStore(conversationDatabasePath(configurationDirectory).toString())
                }
            },
        )
    }
}

internal fun conversationDatabasePath(configurationDirectory: Path): Path =
    configurationDirectory.resolve(CONVERSATION_DATABASE_FILE_NAME)

private const val CONVERSATION_DATABASE_FILE_NAME = "conversations.db"
