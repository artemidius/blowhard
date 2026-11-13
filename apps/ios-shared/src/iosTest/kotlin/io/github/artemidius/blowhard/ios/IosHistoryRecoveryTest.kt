package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.persistence.NativeConversationStores
import io.github.artemidius.blowhard.adapters.persistence.NativeLocalDatabaseFiles
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
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real native SQLite restart recovery evidence for the iOS host. */
internal class IosHistoryRecoveryTest {
    @Test
    internal fun restartRecoversInFlightAttemptExactlyOnce() = runTest {
        val name = "recovery-${NSUUID().UUIDString}.db"
        val transcript = runningIosTranscript()
        NativeConversationStores.open(name).useHistoryStore { store ->
            store.save(StoredConversation(transcript.conversationId, "Recovery", 1, 2))
            store.saveTranscript(transcript)
        }

        try {
            val recovered = ConversationHistorySession.open(RunClock { RunTimestamp(10) }) {
                NativeConversationStores.open(name)
            }
            assertTrue(recovered.available)
            assertEquals(1L, recovered.recoveredAttemptCount)
            assertEquals(ConversationRunState.INTERRUPTED, recovered.initialState.selectedConversation.runState)
            assertTrue(assertNotNull(recovered.initialState.notice).message.contains("not replayed"))
            recovered.close()

            val second = ConversationHistorySession.open(RunClock { RunTimestamp(20) }) {
                NativeConversationStores.open(name)
            }
            assertEquals(0L, second.recoveredAttemptCount)
            assertEquals(ConversationRunState.INTERRUPTED, second.initialState.selectedConversation.runState)
            second.close()
        } finally {
            NativeLocalDatabaseFiles.delete(name)
        }
    }
}

private fun runningIosTranscript(): StoredTranscript {
    val conversationId = ConversationId("ios-recovery")
    val userId = MessageId("ios-user")
    val assistantId = MessageId("ios-assistant")
    return StoredTranscript(
        conversationId = conversationId,
        messages = listOf(
            StoredMessage(userId, 0, StoredMessageRole.USER, "Question", 1),
            StoredMessage(assistantId, 1, StoredMessageRole.ASSISTANT, "Partial", 2),
        ),
        attempts = listOf(
            StoredGenerationAttempt(
                runId = RunId("ios-run"),
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

private suspend fun <T> ConversationStore.useHistoryStore(block: suspend (ConversationStore) -> T): T = try {
    block(this)
} finally {
    close()
}
