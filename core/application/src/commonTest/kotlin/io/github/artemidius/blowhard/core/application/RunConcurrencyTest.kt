package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RunConcurrencyTest {
    @Test
    fun `concurrency limit keeps excess attempts queued`() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val runs = controller(
            maxConcurrentRuns = 1,
            generation = TextGenerationPort { request ->
                flow {
                    if (request.conversationId.value == "conversation-1") releaseFirst.await()
                    emit(GenerationEvent.Completed)
                }
            },
        )
        val first = runs.startRun("conversation-1")
        val second = runs.startRun("conversation-2")
        runCurrent()

        assertIs<RunSnapshot.Running>(runs.current(first))
        assertIs<RunSnapshot.Queued>(runs.current(second))

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertIs<RunSnapshot.Succeeded>(runs.current(first))
        assertIs<RunSnapshot.Succeeded>(runs.current(second))
    }

    @Test
    fun `queued attempt can be cancelled without disturbing active attempt`() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val runs = controller(
            maxConcurrentRuns = 1,
            generation = TextGenerationPort { request ->
                flow {
                    if (request.conversationId.value == "conversation-1") releaseFirst.await()
                    emit(GenerationEvent.Completed)
                }
            },
        )
        val first = runs.startRun("conversation-1")
        val queued = runs.startRun("conversation-2")
        runCurrent()

        assertEquals(CancelRunResult.ACCEPTED, runs.cancel(queued))
        assertIs<RunSnapshot.Cancelled>(runs.current(queued))
        assertIs<RunSnapshot.Running>(runs.current(first))

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertIs<RunSnapshot.Succeeded>(runs.current(first))
    }

    @Test
    fun `all provider failure categories retain safe references`() = runTest {
        val kinds = GenerationFailureKind.entries
        val runs = controller(
            generation = TextGenerationPort { request ->
                val kind = kinds[request.conversationId.value.removePrefix("conversation-").toInt()]
                flowOf(GenerationEvent.Failed(GenerationFailure(kind, "safe-reference")))
            },
        )

        val runIds = kinds.indices.map { runs.startRun("conversation-$it") }
        advanceUntilIdle()

        val mappedKinds = runIds.map { assertIs<RunSnapshot.Failed>(runs.current(it)).failure.kind }
        assertEquals(RunFailureKind.entries.filterNot { it == RunFailureKind.STORAGE_UNAVAILABLE }, mappedKinds)
        runIds.forEach {
            assertEquals("safe-reference", assertIs<RunSnapshot.Failed>(runs.current(it)).failure.reference)
        }
    }

    @Test
    fun `retry guidance is preserved across the application boundary`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flowOf(
                    GenerationEvent.Failed(
                        GenerationFailure(
                            GenerationFailureKind.RATE_LIMITED,
                            retryAdvice = GenerationRetryAdvice.RETRY_AFTER_DELAY,
                            retryAfterSeconds = 17,
                        ),
                    ),
                )
            },
        )

        val runId = runs.startRun("conversation-1")
        advanceUntilIdle()

        val failure = assertIs<RunSnapshot.Failed>(runs.current(runId)).failure
        assertEquals(RunRetryAdvice.RETRY_AFTER_DELAY, failure.retryAdvice)
        assertEquals(17, failure.retryAfterSeconds)
    }

    @Test
    fun `unexpected provider exception is safely redacted`() = runTest {
        val runs = controller(TextGenerationPort { flow { error("private provider payload") } })
        val runId = runs.startRun("conversation-1")
        advanceUntilIdle()

        val failed = assertIs<RunSnapshot.Failed>(runs.current(runId))
        assertEquals(RunFailureKind.UNEXPECTED, failed.failure.kind)
        assertEquals(null, failed.failure.reference)
    }

    @Test
    fun `stream ending without terminal event is a redacted failure`() = runTest {
        val runs = controller(TextGenerationPort { emptyFlow() })
        val runId = runs.startRun("conversation-1")
        advanceUntilIdle()

        val failed = assertIs<RunSnapshot.Failed>(runs.current(runId))
        assertEquals(RunFailureKind.UNEXPECTED, failed.failure.kind)
    }

    @Test
    fun `timestamps come from injected clock in transition order`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flowOf(GenerationEvent.TextDelta("one"), GenerationEvent.Completed)
            },
        )
        val runId = runs.startRun("conversation-1")
        val queued = runs.current(runId)
        advanceUntilIdle()
        val succeeded = runs.current(runId)

        assertTrue(succeeded.updatedAt.epochMilliseconds > queued.updatedAt.epochMilliseconds)
    }

    @Test
    fun `unknown run is not observable`() = runTest {
        val runs = controller(TextGenerationPort { flowOf(GenerationEvent.Completed) })

        assertEquals(ObserveRunResult.NotFound, runs.observe(RunId("missing")))
    }

    @Test
    fun `sequential IDs are stable for one process`() = runTest {
        val factory = SequentialRunIdFactory("attempt")

        val values = List(3) { factory.next().value }

        assertEquals(listOf("attempt-1", "attempt-2", "attempt-3"), values)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        generation: TextGenerationPort,
        maxConcurrentRuns: Int = 2,
    ): DefaultConversationRuns = DefaultConversationRuns(
        generation = generation,
        idFactory = SequentialRunIdFactory(),
        clock = IncrementingClock(),
        coroutineContext = StandardTestDispatcher(testScheduler),
        policy = RunExecutionPolicy(maxConcurrentRuns),
    )

    private suspend fun DefaultConversationRuns.current(runId: RunId): RunSnapshot =
        assertIs<ObserveRunResult.Found>(observe(runId)).snapshots.first()
}
