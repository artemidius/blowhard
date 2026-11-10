package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.persistence.DesktopConversationStores
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopHistorySessionTest {
    @Test
    fun `real desktop startup recovers history and releases the database`() {
        withTemporaryConfiguration { directory ->
            val database = conversationDatabasePath(directory).toString()
            runBlocking {
                val store = DesktopConversationStores.open(database)
                try {
                    store.save(StoredConversation(CONVERSATION_ID, "Recovered", 1, 2))
                    store.saveTranscript(runningTranscript())
                } finally {
                    store.close()
                }
            }

            val session = DesktopHistorySession.open(directory, clock = { RunTimestamp(RECOVERY_TIME) })

            assertTrue(session.available)
            assertEquals(1L, session.recoveredAttemptCount)
            assertEquals(ConversationRunState.INTERRUPTED, session.initialState.selectedConversation.runState)
            assertEquals("partial output", session.initialState.selectedConversation.messages.last().text)
            session.close()
            session.close()

            runBlocking {
                val reopened = DesktopConversationStores.open(database)
                try {
                    val attempt = reopened.loadTranscript(CONVERSATION_ID)!!.attempts.single()
                    assertEquals(StoredAttemptStatus.INTERRUPTED, attempt.status)
                    assertEquals(RECOVERY_TIME, attempt.updatedAtEpochMilliseconds)
                } finally {
                    reopened.close()
                }
            }
        }
    }

    @Test
    fun `corrupt storage exposes a generic unavailable state`() {
        withTemporaryConfiguration { directory ->
            val privateMarker = "private-database-detail"
            Files.writeString(conversationDatabasePath(directory), privateMarker)

            val session = DesktopHistorySession.open(directory)

            assertFalse(session.available)
            val notice = session.initialState.notice!!.message
            assertTrue(notice.contains("history is unavailable"))
            assertFalse(notice.contains(privateMarker))
            assertFalse(notice.contains(directory.toString()))
            session.close()
        }
    }

    private fun runningTranscript(): StoredTranscript {
        val userId = MessageId("user-1")
        val assistantId = MessageId("assistant-1")
        return StoredTranscript(
            conversationId = CONVERSATION_ID,
            messages = listOf(
                StoredMessage(userId, 0, StoredMessageRole.USER, "Question", 1),
                StoredMessage(assistantId, 1, StoredMessageRole.ASSISTANT, "partial output", 2),
            ),
            attempts = listOf(
                StoredGenerationAttempt(
                    runId = RunId("run-1"),
                    userMessageId = userId,
                    assistantMessageId = assistantId,
                    target = GenerationTarget.Demo,
                    parameters = GenerationParameters(),
                    status = StoredAttemptStatus.RUNNING,
                    createdAtEpochMilliseconds = 1,
                    updatedAtEpochMilliseconds = 2,
                ),
            ),
        )
    }

    private fun withTemporaryConfiguration(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("blowhard-desktop-history-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation-1")
        const val RECOVERY_TIME = 10L
    }
}
