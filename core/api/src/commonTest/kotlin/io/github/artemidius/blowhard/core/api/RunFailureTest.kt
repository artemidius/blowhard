package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunFailureTest {
    @Test
    fun `retry delay requires delayed advice and cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            RunFailure(RunFailureKind.RATE_LIMITED, retryAfterSeconds = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            RunFailure(RunFailureKind.RATE_LIMITED, retryAfterSeconds = 1)
        }

        val failure = RunFailure(
            RunFailureKind.RATE_LIMITED,
            retryAdvice = RunRetryAdvice.RETRY_AFTER_DELAY,
            retryAfterSeconds = 1,
        )
        assertEquals(1, failure.retryAfterSeconds)
    }
}
