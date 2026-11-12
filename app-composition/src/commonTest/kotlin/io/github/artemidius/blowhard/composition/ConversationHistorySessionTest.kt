package io.github.artemidius.blowhard.composition

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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationHistorySessionTest {
    @Test
    fun `successful empty history owns and closes its store once`() = runTest {
        val store = RecordingStore()

        val session = ConversationHistorySession.open(openStore = { store })

        assertTrue(session.available)
        assertEquals(store, session.conversationStore)
        assertEquals("New conversation", session.initialState.selectedConversation.title)
        session.close()
        session.close()
        assertEquals(1, store.closeCount)
    }

    @Test
    fun `load failure closes partial storage and exposes no failure detail`() = runTest {
        val store = RecordingStore(failOnList = true)

        val session = ConversationHistorySession.open(openStore = { store })

        assertFalse(session.available)
        assertNull(session.conversationStore)
        assertEquals(1, store.closeCount)
        assertTrue(session.initialState.notice!!.message.contains("history is unavailable"))
        assertFalse(session.initialState.notice!!.message.contains(PRIVATE_FAILURE))
    }

    private companion object {
        const val PRIVATE_FAILURE = "private-storage-detail"
    }
}

internal class RecordingStore(private val failOnList: Boolean = false) : ConversationStore {
    var closeCount: Int = 0
        private set

    override suspend fun save(conversation: StoredConversation): Unit = Unit

    override suspend fun find(id: ConversationId): StoredConversation? = null

    override suspend fun list(): List<StoredConversation> {
        if (failOnList) error("private-storage-detail")
        return emptyList()
    }

    override suspend fun saveCheckpoint(conversation: StoredConversation, transcript: StoredTranscript): Unit = Unit

    override suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult =
        TranscriptSaveResult.CONVERSATION_NOT_FOUND

    override suspend fun loadTranscript(id: ConversationId): StoredTranscript? = null

    override suspend fun recoverInFlightAttempts(recoveredAt: RunTimestamp): ConversationRecoveryResult =
        ConversationRecoveryResult(0)

    override suspend fun delete(id: ConversationId): Boolean = false

    override suspend fun close() {
        closeCount += 1
    }
}
