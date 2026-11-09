package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerationFailureTest {
    @Test
    fun `retry delay requires delayed advice and cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            GenerationFailure(GenerationFailureKind.RATE_LIMITED, retryAfterSeconds = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            GenerationFailure(GenerationFailureKind.RATE_LIMITED, retryAfterSeconds = 1)
        }

        val failure = GenerationFailure(
            GenerationFailureKind.RATE_LIMITED,
            retryAdvice = GenerationRetryAdvice.RETRY_AFTER_DELAY,
            retryAfterSeconds = 1,
        )
        assertEquals(1, failure.retryAfterSeconds)
    }
}
