package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.application.ConversationHistoryEntry
import io.github.artemidius.blowhard.core.application.ConversationHistorySnapshot
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import io.github.artemidius.blowhard.ui.presentation.MessageDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationHistoryPresentationTest {
    @Test
    fun `restored ordering and interrupted partial output map to safe presentation state`() {
        val restored = historyEntry("restored", StoredAttemptStatus.INTERRUPTED, "partial output")
        val older = emptyHistoryEntry("older")

        val state = ConversationHistorySnapshot(listOf(restored, older), recoveredAttemptCount = 1)
            .toConversationUiState()

        assertEquals(ConversationId("restored"), state.selectedConversationId)
        assertEquals(listOf("restored", "older"), state.conversations.map { it.id.value })
        assertEquals(ConversationRunState.INTERRUPTED, state.selectedConversation.runState)
        assertEquals("partial output", state.selectedConversation.messages.last().text)
        assertEquals(MessageDelivery.INTERRUPTED, state.selectedConversation.messages.last().delivery)
        assertEquals(GenerationTarget.Live("mistral-small-latest"), state.selectedConversation.latestTarget)
        assertTrue(state.selectedConversation.messages.last().canRegenerate)
        assertTrue(state.notice!!.message.contains("not replayed"))
    }

    @Test
    fun `unrecovered active state is presented fail closed and cannot regenerate`() {
        val active = historyEntry("active", StoredAttemptStatus.RUNNING, "partial")

        val state = ConversationHistorySnapshot(listOf(active), recoveredAttemptCount = 0)
            .toConversationUiState()

        assertEquals(ConversationRunState.INTERRUPTED, state.selectedConversation.runState)
        assertEquals(MessageDelivery.INTERRUPTED, state.selectedConversation.messages.last().delivery)
        assertFalse(state.selectedConversation.messages.last().canRegenerate)
    }

    @Test
    fun `empty and unavailable history produce explicit deterministic workspaces`() {
        val empty = ConversationHistorySnapshot(emptyList(), 0).toConversationUiState()
        val unavailable = unavailableHistoryState()

        assertEquals(ConversationId("conversation-1"), empty.selectedConversationId)
        assertEquals("Demo conversation", empty.selectedConversation.title)
        assertEquals(null, empty.notice)
        assertTrue(unavailable.notice!!.message.contains("not connected to durable storage"))
    }

    private fun historyEntry(id: String, status: StoredAttemptStatus, output: String): ConversationHistoryEntry {
        val conversationId = ConversationId(id)
        val userId = MessageId("$id-user")
        val assistantId = MessageId("$id-assistant")
        return ConversationHistoryEntry(
            conversation = StoredConversation(conversationId, "Conversation $id", 1, 2),
            transcript = StoredTranscript(
                conversationId = conversationId,
                messages = listOf(
                    StoredMessage(userId, 0, StoredMessageRole.USER, "Question", 1),
                    StoredMessage(assistantId, 1, StoredMessageRole.ASSISTANT, output, 2),
                ),
                attempts = listOf(
                    StoredGenerationAttempt(
                        runId = RunId("$id-run"),
                        userMessageId = userId,
                        assistantMessageId = assistantId,
                        target = GenerationTarget.Live("mistral-small-latest"),
                        parameters = GenerationParameters(maxOutputTokens = 512, temperature = 0.2),
                        status = status,
                        createdAtEpochMilliseconds = 1,
                        updatedAtEpochMilliseconds = 2,
                    ),
                ),
            ),
        )
    }

    private fun emptyHistoryEntry(id: String): ConversationHistoryEntry {
        val conversation = StoredConversation(ConversationId(id), "Conversation $id", 1, 1)
        return ConversationHistoryEntry(conversation, StoredTranscript(conversation.id, emptyList(), emptyList()))
    }
}
