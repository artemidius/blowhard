package io.github.artemidius.blowhard.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artemidius.blowhard.adapters.persistence.AndroidConversationStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidLocalDatabaseFiles
import io.github.artemidius.blowhard.composition.ConversationHistorySession
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.application.RunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android SQLite restart and fail-closed corruption evidence. */
@RunWith(AndroidJUnit4::class)
public class AndroidHistoryRecoveryDeviceTest {
    /** Proves restart preserves snapshots, interrupts active work once, and never replays it. */
    @Test
    public fun restartRecoversInFlightAttemptExactlyOnce(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "recovery-${UUID.randomUUID()}.db"
        val transcript = runningTranscript()
        AndroidConversationStores.open(context, name).useStore { store ->
            store.save(StoredConversation(transcript.conversationId, "Recovery", 1, 2))
            store.saveTranscript(transcript)
        }

        try {
            val recovered = ConversationHistorySession.open(RunClock { RunTimestamp(10) }) {
                AndroidConversationStores.open(context, name)
            }
            assertTrue(recovered.available)
            assertEquals(1L, recovered.recoveredAttemptCount)
            assertEquals(ConversationRunState.INTERRUPTED, recovered.initialState.selectedConversation.runState)
            assertTrue(requireNotNull(recovered.initialState.notice).message.contains("not replayed"))
            recovered.close()

            val second = ConversationHistorySession.open(RunClock { RunTimestamp(20) }) {
                AndroidConversationStores.open(context, name)
            }
            assertEquals(0L, second.recoveredAttemptCount)
            assertEquals(ConversationRunState.INTERRUPTED, second.initialState.selectedConversation.runState)
            second.close()
        } finally {
            AndroidLocalDatabaseFiles.delete(context, name)
        }
    }

    /** Proves Android's corruption handler replaces invalid bytes with an empty, usable store. */
    @Test
    public fun corruptDatabaseIsRecreatedAsEmptyWorkspace(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "corrupt-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        path.writeText("not a sqlite database")

        try {
            val session = ConversationHistorySession.open {
                AndroidConversationStores.open(context, name)
            }

            assertTrue(session.available)
            assertEquals(1, session.initialState.conversations.size)
            assertEquals("New conversation", session.initialState.selectedConversation.title)
            assertEquals(null, session.initialState.notice)
            assertFalse(session.initialState.toString().contains(path.absolutePath))
            session.close()
        } finally {
            AndroidLocalDatabaseFiles.delete(context, name)
        }
    }
}

private fun runningTranscript(): StoredTranscript {
    val conversationId = ConversationId("android-recovery")
    val userId = MessageId("android-user")
    val assistantId = MessageId("android-assistant")
    return StoredTranscript(
        conversationId = conversationId,
        messages = listOf(
            StoredMessage(userId, 0, StoredMessageRole.USER, "Question", 1),
            StoredMessage(assistantId, 1, StoredMessageRole.ASSISTANT, "Partial", 2),
        ),
        attempts = listOf(
            StoredGenerationAttempt(
                runId = RunId("android-run"),
                userMessageId = userId,
                assistantMessageId = assistantId,
                target = GenerationTarget.Live("mistral-small-latest"),
                parameters = GenerationParameters(maxOutputTokens = 128, temperature = 0.2),
                status = StoredAttemptStatus.RUNNING,
                createdAtEpochMilliseconds = 1,
                updatedAtEpochMilliseconds = 2,
            ),
        ),
    )
}

private suspend fun <T> ConversationStore.useStore(block: suspend (ConversationStore) -> T): T = try {
    block(this)
} finally {
    close()
}
