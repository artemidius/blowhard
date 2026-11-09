package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentifierTest {
    @Test
    fun `conversation ID preserves an opaque value`() {
        val id = ConversationId("conversation-01")

        assertEquals("conversation-01", id.value)
    }

    @Test
    fun `run ID rejects blank values`() {
        assertFailsWith<IllegalArgumentException> {
            RunId("  ")
        }
    }
}
