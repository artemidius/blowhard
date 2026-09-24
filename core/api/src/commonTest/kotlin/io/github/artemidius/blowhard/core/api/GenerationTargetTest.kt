package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerationTargetTest {
    @Test
    fun `live target keeps a validated model identifier`() {
        assertEquals("mistral-small-latest", GenerationTarget.Live("mistral-small-latest").modelId)
    }

    @Test
    fun `live target rejects blank oversized and unsafe identifiers`() {
        listOf(" ", "a".repeat(257), "model id", "model\nname").forEach { modelId ->
            assertFailsWith<IllegalArgumentException> { GenerationTarget.Live(modelId) }
        }
    }
}
