package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoredConversationTest {
    @Test
    fun `accepts bounded chronological metadata`() {
        val stored = StoredConversation(
            id = ConversationId("conversation-1"),
            title = "Architecture review",
            createdAtEpochMilliseconds = 10,
            updatedAtEpochMilliseconds = 20,
        )

        assertEquals("Architecture review", stored.title)
    }

    @Test
    fun `rejects invalid title and chronology`() {
        assertFailsWith<IllegalArgumentException> { conversation(title = " ") }
        assertFailsWith<IllegalArgumentException> {
            conversation(title = "x".repeat(StoredConversation.MAX_TITLE_LENGTH + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            conversation(createdAt = 2, updatedAt = 1)
        }
    }

    private fun conversation(
        title: String = "Conversation",
        createdAt: Long = 1,
        updatedAt: Long = createdAt,
    ): StoredConversation = StoredConversation(
        id = ConversationId("conversation-1"),
        title = title,
        createdAtEpochMilliseconds = createdAt,
        updatedAtEpochMilliseconds = updatedAt,
    )
}
