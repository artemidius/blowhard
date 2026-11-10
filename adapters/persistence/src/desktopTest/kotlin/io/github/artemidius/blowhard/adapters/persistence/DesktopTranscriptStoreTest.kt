package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.TranscriptSaveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DesktopTranscriptStoreTest {
    @Test
    internal fun transcriptRoundTripsOrderedMessagesAndAttemptSnapshots() = withTemporaryDatabase { path ->
        DesktopConversationStores.open(path).useStore { store ->
            store.save(StoredFixtures.conversation(CONVERSATION_ID, "Stored chat", 1, UPDATED_AT))
            val transcript = completeTranscript()

            assertEquals(TranscriptSaveResult.SAVED, store.saveTranscript(transcript))

            val loaded = assertNotNull(store.loadTranscript(ConversationId(CONVERSATION_ID)))
            assertEquals(transcript.messages.sortedBy { it.sequence }, loaded.messages)
            assertEquals(transcript.attempts.sortedBy { it.createdAtEpochMilliseconds }, loaded.attempts)
        }
    }

    @Test
    internal fun missingConversationRejectsTranscriptWithoutRows() = withTemporaryDatabase { path ->
        DesktopConversationStores.open(path).useStore { store ->
            val transcript = completeTranscript()

            assertEquals(TranscriptSaveResult.CONVERSATION_NOT_FOUND, store.saveTranscript(transcript))
            assertNull(store.loadTranscript(ConversationId(CONVERSATION_ID)))
        }
    }

    @Test
    internal fun replacementRollsBackWhenAttemptIdentityConflicts() = withTemporaryDatabase { path ->
        DesktopConversationStores.open(path).useStore { store ->
            store.save(StoredFixtures.conversation("first", "First", 1, 2))
            store.save(StoredFixtures.conversation("second", "Second", 1, 2))
            val first = singleAttemptTranscript("first", "shared-run")
            val originalSecond = singleAttemptTranscript("second", "second-run")
            store.saveTranscript(first)
            store.saveTranscript(originalSecond)

            val failure = runCatching {
                store.saveTranscript(singleAttemptTranscript("second", "shared-run"))
            }.exceptionOrNull()

            assertNotNull(failure)
            assertEquals(originalSecond, store.loadTranscript(ConversationId("second")))
        }
    }

    @Test
    internal fun deletingConversationRemovesItsTranscriptRows() = withTemporaryDatabase { path ->
        DesktopConversationStores.open(path).useStore { store ->
            store.save(StoredFixtures.conversation(CONVERSATION_ID, "Delete", 1, 2))
            store.saveTranscript(completeTranscript())

            store.delete(ConversationId(CONVERSATION_ID))

            assertNull(store.loadTranscript(ConversationId(CONVERSATION_ID)))
            val replacementId = "replacement"
            store.save(
                StoredFixtures.conversation(
                    replacementId,
                    "Replacement",
                    REPLACEMENT_CREATED_AT,
                    REPLACEMENT_UPDATED_AT,
                ),
            )
            val reusedIdentities = completeTranscript().copy(conversationId = ConversationId(replacementId))
            assertEquals(TranscriptSaveResult.SAVED, store.saveTranscript(reusedIdentities))
        }
    }

    private fun completeTranscript() = StoredFixtures.transcript(
        conversationId = CONVERSATION_ID,
        messages = listOf(
            StoredFixtures.message("assistant-2", LATER_SEQUENCE, StoredMessageRole.ASSISTANT, "Later"),
            StoredFixtures.message("user-1", 0, StoredMessageRole.USER, "Grüß dich 👋"),
            StoredFixtures.message("assistant-1", 1, StoredMessageRole.ASSISTANT, "Partial"),
            StoredFixtures.message("user-2", 2, StoredMessageRole.USER, "Try again"),
        ),
        attempts = listOf(
            StoredFixtures.attempt("run-2", "user-2", "assistant-2", createdAt = 3, updatedAt = 4),
            StoredFixtures.attempt(
                runId = "run-1",
                userMessageId = "user-1",
                assistantMessageId = "assistant-1",
                target = GenerationTarget.Live("mistral-small-latest"),
                parameters = GenerationParameters(MAX_OUTPUT_TOKENS, TEMPERATURE),
                status = StoredAttemptStatus.FAILED,
                failure = RunFailure(
                    RunFailureKind.RATE_LIMITED,
                    reference = "safe-reference",
                    retryAdvice = RunRetryAdvice.RETRY_AFTER_DELAY,
                    retryAfterSeconds = 12,
                ),
                createdAt = 1,
                updatedAt = 2,
            ),
        ),
    )

    private fun singleAttemptTranscript(conversationId: String, runId: String) = StoredFixtures.transcript(
        conversationId = conversationId,
        messages = listOf(
            StoredFixtures.message("$conversationId-user", 0, StoredMessageRole.USER, "Question"),
            StoredFixtures.message("$conversationId-assistant", 1, StoredMessageRole.ASSISTANT, "Answer"),
        ),
        attempts = listOf(
            StoredFixtures.attempt(runId, "$conversationId-user", "$conversationId-assistant"),
        ),
    )

    private companion object {
        const val CONVERSATION_ID = "conversation-1"
        const val UPDATED_AT = 8L
        const val LATER_SEQUENCE = 3L
        const val MAX_OUTPUT_TOKENS = 1_024
        const val TEMPERATURE = 0.2
        const val REPLACEMENT_CREATED_AT = 3L
        const val REPLACEMENT_UPDATED_AT = 4L
    }
}
