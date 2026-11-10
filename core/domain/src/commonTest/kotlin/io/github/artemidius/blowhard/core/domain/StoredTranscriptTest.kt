package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoredTranscriptTest {
    @Test
    fun `valid transcript retains immutable routing and outcome`() {
        val transcript = transcript()

        assertEquals(GenerationTarget.Live("mistral-small-latest"), transcript.attempts.single().target)
        assertEquals(StoredAttemptStatus.SUCCEEDED, transcript.attempts.single().status)
    }

    @Test
    fun `transcript rejects duplicate identity sequence and attempt references`() {
        val original = messages()
        assertFailsWith<IllegalArgumentException> {
            transcript(messages = original + original.first().copy(sequence = 2))
        }
        assertFailsWith<IllegalArgumentException> {
            transcript(messages = original + message("other", 1, StoredMessageRole.ASSISTANT, "Other"))
        }
        assertFailsWith<IllegalArgumentException> {
            transcript(attempt = attempt(userMessageId = MessageId("missing")))
        }
    }

    @Test
    fun `attempt failure details match failed status`() {
        assertFailsWith<IllegalArgumentException> {
            attempt(status = StoredAttemptStatus.FAILED)
        }
        assertFailsWith<IllegalArgumentException> {
            attempt(
                status = StoredAttemptStatus.SUCCEEDED,
                failure = RunFailure(RunFailureKind.UNAVAILABLE),
            )
        }
    }

    private fun transcript(
        messages: List<StoredMessage> = messages(),
        attempt: StoredGenerationAttempt = attempt(),
    ): StoredTranscript = StoredTranscript(ConversationId("conversation-1"), messages, listOf(attempt))

    private fun messages(): List<StoredMessage> = listOf(
        message("user-1", 0, StoredMessageRole.USER, "Hello"),
        message("assistant-1", 1, StoredMessageRole.ASSISTANT, "Hi"),
    )

    private fun message(id: String, sequence: Long, role: StoredMessageRole, text: String): StoredMessage =
        StoredMessage(MessageId(id), sequence, role, text, createdAtEpochMilliseconds = sequence + 1)

    private fun attempt(
        userMessageId: MessageId = MessageId("user-1"),
        status: StoredAttemptStatus = StoredAttemptStatus.SUCCEEDED,
        failure: RunFailure? = null,
    ): StoredGenerationAttempt = StoredGenerationAttempt(
        runId = RunId("run-1"),
        userMessageId = userMessageId,
        assistantMessageId = MessageId("assistant-1"),
        target = GenerationTarget.Live("mistral-small-latest"),
        parameters = GenerationParameters(maxOutputTokens = 1_024, temperature = 0.2),
        status = status,
        failure = failure,
        createdAtEpochMilliseconds = 1,
        updatedAtEpochMilliseconds = 2,
    )
}
