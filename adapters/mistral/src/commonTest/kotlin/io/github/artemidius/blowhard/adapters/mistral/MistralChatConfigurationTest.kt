package io.github.artemidius.blowhard.adapters.mistral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MistralChatConfigurationTest {
    @Test
    fun `model and output token bounds are validated`() {
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration(" ") }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("model id") }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("x".repeat(257)) }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("model", 0) }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("model", 1_000_001) }

        assertNull(MistralChatConfiguration("model").maxOutputTokens)
        assertEquals(1_000_000, MistralChatConfiguration("model", 1_000_000).maxOutputTokens)
    }
}
