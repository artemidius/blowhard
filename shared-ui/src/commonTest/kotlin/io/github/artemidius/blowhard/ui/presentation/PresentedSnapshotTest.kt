package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentedSnapshotTest {
    @Test
    fun `every failure category maps to copy without its reference`() {
        RunFailureKind.entries.forEach { kind ->
            val snapshot = RunSnapshot.Failed(
                runId = RunId("run"),
                conversationId = ConversationId("conversation"),
                updatedAt = RunTimestamp(1L),
                partialOutput = "partial",
                failure = RunFailure(kind, "private-reference"),
            )

            assertFalse(snapshot.present().notice!!.message.contains("private-reference"))
        }
    }

    @Test
    fun `regeneration follows conservative retry advice`() {
        val explicit = failedSnapshot(RunRetryAdvice.EXPLICIT_NEW_ATTEMPT)
        val delayed = failedSnapshot(RunRetryAdvice.RETRY_AFTER_DELAY, retryAfterSeconds = 10)
        val forbidden = failedSnapshot(RunRetryAdvice.DO_NOT_RETRY)

        assertTrue(explicit.present().canRegenerate)
        assertFalse(delayed.present().canRegenerate)
        assertFalse(forbidden.present().canRegenerate)
    }

    private fun failedSnapshot(advice: RunRetryAdvice, retryAfterSeconds: Long? = null): RunSnapshot.Failed =
        RunSnapshot.Failed(
            runId = RunId("run"),
            conversationId = ConversationId("conversation"),
            updatedAt = RunTimestamp(1L),
            partialOutput = "partial",
            failure = RunFailure(
                kind = RunFailureKind.UNAVAILABLE,
                retryAdvice = advice,
                retryAfterSeconds = retryAfterSeconds,
            ),
        )
}
