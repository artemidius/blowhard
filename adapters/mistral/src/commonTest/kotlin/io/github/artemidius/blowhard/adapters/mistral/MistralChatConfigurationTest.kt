package io.github.artemidius.blowhard.adapters.mistral

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MistralChatConfigurationTest {
    @Test
    fun `model identifier bounds are validated`() {
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration(" ") }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("model id") }
        assertFailsWith<IllegalArgumentException> { MistralChatConfiguration("x".repeat(257)) }
        MistralChatConfiguration("model")
    }
}
