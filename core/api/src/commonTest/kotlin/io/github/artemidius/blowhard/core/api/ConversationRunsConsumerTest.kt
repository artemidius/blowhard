package io.github.artemidius.blowhard.core.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConversationRunsConsumerTest {
    @Test
    fun `headless consumer can use the public contract`() = runTest {
        val conversationId = ConversationId("conversation-01")
        val runId = RunId("run-01")
        val client = StubConversationRuns(conversationId, runId)

        val started = client.start(StartRunRequest(conversationId, "Hello"))
        val observation = client.observe(assertIs<StartRunResult.Started>(started).runId)
        val found = assertIs<ObserveRunResult.Found>(observation)

        assertEquals(
            RunSnapshot.Queued(runId, conversationId),
            assertIs<RunSnapshot.Queued>(found.snapshots.first()),
        )
    }

    private class StubConversationRuns(
        private val conversationId: ConversationId,
        private val runId: RunId,
    ) : ConversationRuns {
        override suspend fun start(request: StartRunRequest): StartRunResult =
            StartRunResult.Started(runId)

        override suspend fun observe(runId: RunId): ObserveRunResult =
            ObserveRunResult.Found(flowOf(RunSnapshot.Queued(runId, conversationId)))

        override suspend fun cancel(runId: RunId): CancelRunResult = CancelRunResult.ACCEPTED
    }
}
