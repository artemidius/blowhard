package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.domain.ConversationRecoveryResult
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.core.domain.TranscriptSaveResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationHistoryLoaderTest {
    @Test
    fun `recovery completes before ordered history is loaded`() = runTest {
        val first = conversation("first", updatedAt = 20)
        val second = conversation("second", updatedAt = 10)
        val store = RecordingConversationStore(listOf(first, second), recoveredAttempts = 2)

        val snapshot = ConversationHistoryLoader(store) { RunTimestamp(RECOVERY_TIME) }.load()

        assertEquals(listOf("recover:$RECOVERY_TIME", "list", "load:first", "load:second"), store.operations)
        assertEquals(listOf(first, second), snapshot.entries.map { it.conversation })
        assertEquals(2L, snapshot.recoveredAttemptCount)
    }

    @Test
    fun `history rejects mismatched or disappearing durable state`() = runTest {
        val stored = conversation("present", updatedAt = 1)
        assertFailsWith<IllegalArgumentException> {
            ConversationHistoryEntry(stored, StoredTranscript(ConversationId("other"), emptyList(), emptyList()))
        }

        val store = RecordingConversationStore(listOf(stored), recoveredAttempts = 0, transcriptsAvailable = false)
        assertFailsWith<IllegalStateException> {
            ConversationHistoryLoader(store) { RunTimestamp(RECOVERY_TIME) }.load()
        }
    }

    @Test
    fun `history snapshot rejects duplicate conversations and invalid recovery count`() {
        val stored = conversation("duplicate", updatedAt = 1)
        val entry = ConversationHistoryEntry(stored, StoredTranscript(stored.id, emptyList(), emptyList()))

        assertFailsWith<IllegalArgumentException> { ConversationHistorySnapshot(listOf(entry, entry), 0) }
        assertFailsWith<IllegalArgumentException> { ConversationHistorySnapshot(emptyList(), -1) }
    }

    private companion object {
        const val RECOVERY_TIME = 100L
    }
}

private class RecordingConversationStore(
    private val conversations: List<StoredConversation>,
    private val recoveredAttempts: Long,
    private val transcriptsAvailable: Boolean = true,
) : ConversationStore {
    val operations = mutableListOf<String>()

    override suspend fun recoverInFlightAttempts(recoveredAt: RunTimestamp): ConversationRecoveryResult {
        operations += "recover:${recoveredAt.epochMilliseconds}"
        return ConversationRecoveryResult(recoveredAttempts)
    }

    override suspend fun list(): List<StoredConversation> {
        operations += "list"
        return conversations
    }

    override suspend fun loadTranscript(id: ConversationId): StoredTranscript? {
        operations += "load:${id.value}"
        return if (transcriptsAvailable) StoredTranscript(id, emptyList(), emptyList()) else null
    }

    override suspend fun save(conversation: StoredConversation) = unsupported()

    override suspend fun find(id: ConversationId): StoredConversation? = unsupported()

    override suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult = unsupported()

    override suspend fun delete(id: ConversationId): Boolean = unsupported()

    override suspend fun close(): Unit = Unit

    private fun unsupported(): Nothing = error("Not used by history loader tests")
}

private fun conversation(id: String, updatedAt: Long): StoredConversation = StoredConversation(
    id = ConversationId(id),
    title = "Conversation $id",
    createdAtEpochMilliseconds = 1,
    updatedAtEpochMilliseconds = updatedAt,
)
