package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConversationRunsTest {
    @Test
    fun `two observers share one provider execution`() = runTest {
        var executions = 0
        val runs = controller(
            TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.TextDelta("Hello"), GenerationEvent.Completed)
            },
        )

        val runId = runs.startRun("conversation-1")
        val first = async { runs.snapshots(runId).first { it is RunSnapshot.Succeeded } }
        val second = async { runs.snapshots(runId).first { it is RunSnapshot.Succeeded } }
        advanceUntilIdle()

        assertEquals(1, executions)
        assertEquals(first.await(), second.await())
    }

    @Test
    fun `cancellation produces one immutable terminal snapshot`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flow {
                    emit(GenerationEvent.TextDelta("partial"))
                    awaitCancellation()
                }
            },
        )
        val runId = runs.startRun("conversation-1")
        runCurrent()

        assertEquals(CancelRunResult.ACCEPTED, runs.cancel(runId))
        assertEquals(CancelRunResult.ALREADY_FINISHED, runs.cancel(runId))
        assertEquals("partial", assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first()).partialOutput)
    }

    @Test
    fun `events after completion cannot mutate terminal output`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flowOf(
                    GenerationEvent.TextDelta("final"),
                    GenerationEvent.Completed,
                    GenerationEvent.TextDelta("late"),
                )
            },
        )

        val runId = runs.startRun("conversation-1")
        advanceUntilIdle()

        assertEquals("final", assertIs<RunSnapshot.Succeeded>(runs.snapshots(runId).first()).output)
    }

    @Test
    fun `same conversation is exclusive while other conversations remain isolated`() = runTest {
        val runs = controller(
            TextGenerationPort { request ->
                if (request.conversationId.value == "conversation-1") {
                    flow { awaitCancellation() }
                } else {
                    flowOf(GenerationEvent.TextDelta("other"), GenerationEvent.Completed)
                }
            },
        )
        runs.startRun("conversation-1")
        runCurrent()

        val duplicate = runs.start(StartRunRequest(ConversationId("conversation-1"), "Hello"))
        val other = runs.startRun("conversation-2")
        advanceUntilIdle()

        assertEquals(
            StartRunRejection.CONVERSATION_BUSY,
            assertIs<StartRunResult.Rejected>(duplicate).reason,
        )
        assertEquals("other", assertIs<RunSnapshot.Succeeded>(runs.snapshots(other).first()).output)
    }

    @Test
    fun `blank prompts never invoke the provider`() = runTest {
        var executions = 0
        val runs = controller(
            TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.Completed)
            },
        )

        val result = runs.start(StartRunRequest(ConversationId("conversation-1"), " \n "))

        assertEquals(StartRunRejection.BLANK_PROMPT, assertIs<StartRunResult.Rejected>(result).reason)
        assertEquals(0, executions)
    }

    @Test
    fun `close cancels active work and rejects future starts`() = runTest {
        val runs = controller(TextGenerationPort { flow { awaitCancellation() } })
        val runId = runs.startRun("conversation-1")
        runCurrent()

        runs.close()
        val afterClose = runs.start(StartRunRequest(ConversationId("conversation-2"), "Hello"))

        assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first())
        assertEquals(StartRunRejection.LIFECYCLE_CLOSED, assertIs<StartRunResult.Rejected>(afterClose).reason)
    }

    @Test
    fun `concurrent cancellation requests still write one terminal state`() = runTest {
        val runs = controller(TextGenerationPort { flow { awaitCancellation() } })
        val runId = runs.startRun("conversation-1")
        runCurrent()

        val first = async { runs.cancel(runId) }
        val second = async { runs.cancel(runId) }
        advanceUntilIdle()

        assertEquals(
            setOf(CancelRunResult.ACCEPTED, CancelRunResult.ALREADY_FINISHED),
            setOf(first.await(), second.await()),
        )
        assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first())
    }

    @Test
    fun `cancelling unknown run is a typed result`() = runTest {
        val runs = controller(TextGenerationPort { flowOf(GenerationEvent.Completed) })

        assertEquals(CancelRunResult.NOT_FOUND, runs.cancel(RunId("missing")))
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
}

internal suspend fun DefaultConversationRuns.startRun(conversation: String): RunId {
    val result = start(StartRunRequest(ConversationId(conversation), "Hello"))
    return assertIs<StartRunResult.Started>(result).runId
}

internal suspend fun DefaultConversationRuns.snapshots(runId: RunId): kotlinx.coroutines.flow.Flow<RunSnapshot> =
    assertIs<ObserveRunResult.Found>(observe(runId)).snapshots

internal class IncrementingClock : RunClock {
    private var next = 1_000L

    override fun now(): RunTimestamp = RunTimestamp(next++)
}
