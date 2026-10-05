package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.ConversationRecoveryResult
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.core.domain.TranscriptSaveResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDeletionTest {
    @Test
    fun `deletion reports durable row ownership without provider work`() = runTest {
        val store = DeletionStore(deleteResult = true)
        val fixture = fixture(store)

        assertEquals(ConversationDeletionResult.DELETED, fixture.delete(CONVERSATION_ID))
        store.deleteResult = false
        assertEquals(ConversationDeletionResult.NOT_FOUND, fixture.delete(CONVERSATION_ID))

        assertEquals(listOf(CONVERSATION_ID, CONVERSATION_ID), store.deletedIds)
        assertEquals(0, fixture.providerCalls)
        fixture.close()
    }

    @Test
    fun `active conversation is fenced before storage deletion`() = runTest {
        val store = DeletionStore(deleteResult = true)
        val fixture = fixture(store)
        assertIs<StartRunResult.Started>(fixture.start(StartRunRequest(CONVERSATION_ID, "active")))

        assertEquals(ConversationDeletionResult.CONVERSATION_BUSY, fixture.delete(CONVERSATION_ID))
        assertEquals(emptyList(), store.deletedIds)
        fixture.close()
    }

    @Test
    fun `missing or failed storage never claims successful deletion`() = runTest {
        val unavailable = fixture(store = null)
        assertEquals(ConversationDeletionResult.STORAGE_UNAVAILABLE, unavailable.delete(CONVERSATION_ID))
        unavailable.close()

        val store = DeletionStore(deleteResult = true, failDeletion = true)
        val failing = fixture(store)
        assertEquals(ConversationDeletionResult.STORAGE_UNAVAILABLE, failing.delete(CONVERSATION_ID))

        store.failDeletion = false
        assertIs<StartRunResult.Started>(failing.start(StartRunRequest(CONVERSATION_ID, "reservation released")))
        failing.close()
    }

    @Test
    fun `start cannot race a reserved deletion`() = runTest {
        val deletionStarted = CompletableDeferred<Unit>()
        val releaseDeletion = CompletableDeferred<Unit>()
        val store = DeletionStore(
            deleteResult = true,
            beforeDelete = {
                deletionStarted.complete(Unit)
                releaseDeletion.await()
            },
        )
        val fixture = fixture(store)
        val deletion = launch { fixture.delete(CONVERSATION_ID) }
        deletionStarted.await()

        val start = fixture.start(StartRunRequest(CONVERSATION_ID, "racing start"))
        assertEquals(StartRunResult.Rejected(StartRunRejection.CONVERSATION_BUSY), start)

        releaseDeletion.complete(Unit)
        deletion.join()
        fixture.close()
    }

    @Test
    fun `closed lifecycle rejects deletion without touching storage`() = runTest {
        val store = DeletionStore(deleteResult = true)
        val fixture = fixture(store)
        fixture.close()

        assertEquals(ConversationDeletionResult.LIFECYCLE_CLOSED, fixture.delete(CONVERSATION_ID))
        assertEquals(emptyList(), store.deletedIds)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(store: DeletionStore?): DeletionFixture {
        var providerCalls = 0
        val runs = DefaultConversationRuns(
            generation = TextGenerationPort {
                providerCalls += 1
                flow<GenerationEvent> { awaitCancellation() }
            },
            idFactory = SequentialRunIdFactory("deletion"),
            clock = IncrementingClock(),
            coroutineContext = StandardTestDispatcher(testScheduler),
            conversationStore = store,
        )
        return DeletionFixture(runs) { providerCalls }
    }
}

private class DeletionFixture(private val runs: DefaultConversationRuns, private val providerCallCount: () -> Int) {
    val providerCalls: Int
        get() = providerCallCount()

    suspend fun delete(id: ConversationId): ConversationDeletionResult = runs.delete(id)

    suspend fun start(request: StartRunRequest): StartRunResult = runs.start(request)

    suspend fun close() = runs.close()
}

private class DeletionStore(
    var deleteResult: Boolean,
    var failDeletion: Boolean = false,
    private val beforeDelete: suspend () -> Unit = {},
) : ConversationStore {
    val deletedIds = mutableListOf<ConversationId>()

    override suspend fun saveCheckpoint(conversation: StoredConversation, transcript: StoredTranscript) = Unit

    override suspend fun delete(id: ConversationId): Boolean {
        beforeDelete()
        if (failDeletion) error("synthetic private storage failure")
        deletedIds += id
        return deleteResult
    }

    override suspend fun save(conversation: StoredConversation): Unit = unsupported()

    override suspend fun find(id: ConversationId): StoredConversation? = null

    override suspend fun list(): List<StoredConversation> = unsupported()

    override suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult = unsupported()

    override suspend fun loadTranscript(id: ConversationId): StoredTranscript? = null

    override suspend fun recoverInFlightAttempts(recoveredAt: RunTimestamp): ConversationRecoveryResult = unsupported()

    override suspend fun close() = Unit

    private fun unsupported(): Nothing = error("Not used by conversation deletion tests")
}

private val CONVERSATION_ID = ConversationId("deletion-conversation")
