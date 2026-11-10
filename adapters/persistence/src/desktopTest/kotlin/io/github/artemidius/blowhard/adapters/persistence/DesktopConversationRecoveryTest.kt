package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class DesktopConversationRecoveryTest {
    @Test
    internal fun restartInterruptsOnlyInFlightAttemptsWithoutReplayingOrLosingSnapshots() =
        withTemporaryDatabase { path ->
            val queued = transcript("queued", StoredAttemptStatus.QUEUED, "")
            val running = transcript("running", StoredAttemptStatus.RUNNING, "partial output", updatedAt = LATER_UPDATE)
            val terminal = terminalTranscript()
            DesktopConversationStores.open(path).useStore { store ->
                listOf(queued, running, terminal).forEach { value ->
                    store.save(StoredFixtures.conversation(value.conversationId.value, "Stored", 1, 2))
                    store.saveTranscript(value)
                }
            }

            DesktopConversationStores.open(path).useStore { store ->
                val recovery = store.recoverInFlightAttempts(RunTimestamp(RECOVERY_TIME))

                assertEquals(2L, recovery.interruptedAttemptCount)
                assertRecovered(store.loadTranscript(queued.conversationId), queued, RECOVERY_TIME)
                assertRecovered(store.loadTranscript(running.conversationId), running, LATER_UPDATE)
                assertEquals(terminal, store.loadTranscript(terminal.conversationId))

                assertEquals(
                    0L,
                    store.recoverInFlightAttempts(RunTimestamp(SECOND_RECOVERY_TIME)).interruptedAttemptCount,
                )
                assertRecovered(store.loadTranscript(queued.conversationId), queued, RECOVERY_TIME)
            }
        }

    private fun assertRecovered(actual: StoredTranscript?, original: StoredTranscript, updatedAt: Long) {
        val loaded = assertNotNull(actual)
        assertEquals(original.messages, loaded.messages)
        assertEquals(
            original.attempts.single().copy(
                status = StoredAttemptStatus.INTERRUPTED,
                updatedAtEpochMilliseconds = updatedAt,
            ),
            loaded.attempts.single(),
        )
    }

    private fun transcript(
        conversationId: String,
        status: StoredAttemptStatus,
        assistantText: String,
        updatedAt: Long = INITIAL_UPDATE,
    ): StoredTranscript = StoredFixtures.transcript(
        conversationId = conversationId,
        messages = listOf(
            StoredFixtures.message("$conversationId-user", 0, StoredMessageRole.USER, "Question"),
            StoredFixtures.message("$conversationId-assistant", 1, StoredMessageRole.ASSISTANT, assistantText),
        ),
        attempts = listOf(
            StoredFixtures.attempt(
                runId = "$conversationId-run",
                userMessageId = "$conversationId-user",
                assistantMessageId = "$conversationId-assistant",
                target = GenerationTarget.Live("mistral-small-latest"),
                parameters = GenerationParameters(maxOutputTokens = 512, temperature = 0.2),
                status = status,
                createdAt = 1,
                updatedAt = updatedAt,
            ),
        ),
    )

    private fun terminalTranscript(): StoredTranscript {
        val conversationId = "terminal"
        val statuses = listOf(
            StoredAttemptStatus.SUCCEEDED,
            StoredAttemptStatus.FAILED,
            StoredAttemptStatus.CANCELLED,
            StoredAttemptStatus.INTERRUPTED,
        )
        val messages = statuses.flatMapIndexed { index, status ->
            val sequence = index.toLong() * 2
            listOf(
                StoredFixtures.message("$conversationId-user-$status", sequence, StoredMessageRole.USER, "Question"),
                StoredFixtures.message(
                    "$conversationId-assistant-$status",
                    sequence + 1,
                    StoredMessageRole.ASSISTANT,
                    "Answer",
                ),
            )
        }
        val attempts = statuses.mapIndexed { index, status ->
            StoredFixtures.attempt(
                runId = "$conversationId-run-$status",
                userMessageId = "$conversationId-user-$status",
                assistantMessageId = "$conversationId-assistant-$status",
                status = status,
                failure = status.failure(),
                createdAt = index.toLong() + 1,
                updatedAt = index.toLong() + 1,
            )
        }
        return StoredFixtures.transcript(conversationId, messages, attempts)
    }

    private fun StoredAttemptStatus.failure(): RunFailure? =
        if (this == StoredAttemptStatus.FAILED) RunFailure(RunFailureKind.UNAVAILABLE) else null

    private companion object {
        const val INITIAL_UPDATE = 5L
        const val RECOVERY_TIME = 10L
        const val LATER_UPDATE = 20L
        const val SECOND_RECOVERY_TIME = 30L
    }
}
