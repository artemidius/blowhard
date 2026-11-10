package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTerminalStatus
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.api.TokenUsage
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RunInspectionTest {
    @Test
    fun `only terminal snapshots can finish an inspection`() {
        val runId = RunId("non-terminal")
        val acceptedAt = RunTimestamp(1)
        val inspection = RunInspection(
            runId = runId,
            conversationId = CONVERSATION_ID,
            target = GenerationTarget.Demo,
            parameters = GenerationParameters(),
            events = listOf(RunInspectionEvent.Accepted(acceptedAt)),
        )

        listOf(
            RunSnapshot.Queued(runId, CONVERSATION_ID, acceptedAt),
            RunSnapshot.Running(runId, CONVERSATION_ID, RunTimestamp(2), "sensitive partial output"),
        ).forEach { snapshot ->
            assertFailsWith<IllegalStateException> { inspection.finished(snapshot) }
        }
    }

    @Test
    fun `long successful stream produces bounded content-free inspection with optional usage`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flow {
                    repeat(1_000) { emit(GenerationEvent.TextDelta("sensitive-output-$it")) }
                    emit(GenerationEvent.Usage(TokenUsage(inputTokens = 11, outputTokens = 22, totalTokens = 33)))
                    emit(GenerationEvent.Completed)
                }
            },
        )
        val started = assertIs<StartRunResult.Started>(
            runs.start(StartRunRequest(CONVERSATION_ID, "sensitive prompt and credential")),
        )
        advanceUntilIdle()

        val inspection = assertIs<RunInspectionResult.Found>(runs.inspect(started.runId)).inspection

        assertEquals(TokenUsage(11, 22, 33), inspection.usage)
        assertEquals(
            listOf(
                RunInspectionEvent.Accepted::class,
                RunInspectionEvent.Started::class,
                RunInspectionEvent.Finished::class,
            ),
            inspection.events.map { it::class },
        )
        assertEquals(
            RunTerminalStatus.SUCCEEDED,
            assertIs<RunInspectionEvent.Finished>(inspection.events.last()).status,
        )
        assertTrue(inspection.events.size <= RunInspection.MAX_EVENTS)
        assertFalse(inspection.toString().contains("sensitive prompt"))
        assertFalse(inspection.toString().contains("sensitive-output"))
        runs.close()
    }

    @Test
    fun `failure inspection allowlists classification and retry decision but omits reference`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flowOf(
                    GenerationEvent.Failed(
                        GenerationFailure(
                            kind = GenerationFailureKind.RATE_LIMITED,
                            reference = "sensitive-provider-reference",
                            retryAdvice = GenerationRetryAdvice.RETRY_AFTER_DELAY,
                            retryAfterSeconds = 9,
                        ),
                    ),
                )
            },
        )
        val started = assertIs<StartRunResult.Started>(
            runs.start(StartRunRequest(CONVERSATION_ID, "classified failure")),
        )
        advanceUntilIdle()

        val inspection = assertIs<RunInspectionResult.Found>(runs.inspect(started.runId)).inspection
        val finished = assertIs<RunInspectionEvent.Finished>(inspection.events.last())

        assertEquals(RunTerminalStatus.FAILED, finished.status)
        assertEquals(RunFailureKind.RATE_LIMITED, finished.failureKind)
        assertEquals(RunRetryAdvice.RETRY_AFTER_DELAY, finished.retryAdvice)
        assertEquals(9, finished.retryAfterSeconds)
        assertNull(inspection.usage)
        assertFalse(inspection.toString().contains("sensitive-provider-reference"))
        runs.close()
    }

    @Test
    fun `cancellation and unknown lookup have explicit inspection outcomes`() = runTest {
        val runs = controller(TextGenerationPort { flow { awaitCancellation() } })
        assertEquals(
            RunInspectionResult.NotFound,
            runs.inspect(io.github.artemidius.blowhard.core.api.RunId("missing")),
        )
        val started = assertIs<StartRunResult.Started>(
            runs.start(StartRunRequest(CONVERSATION_ID, "cancel safely")),
        )
        runCurrent()

        runs.cancel(started.runId)
        val inspection = assertIs<RunInspectionResult.Found>(runs.inspect(started.runId)).inspection

        assertEquals(
            RunTerminalStatus.CANCELLED,
            assertIs<RunInspectionEvent.Finished>(inspection.events.last()).status,
        )
        runs.close()
    }

    private fun kotlinx.coroutines.test.TestScope.controller(generation: TextGenerationPort): DefaultConversationRuns =
        DefaultConversationRuns(
            generation = generation,
            idFactory = SequentialRunIdFactory("inspection"),
            clock = IncrementingClock(),
            coroutineContext = StandardTestDispatcher(testScheduler),
        )
}

private val CONVERSATION_ID = ConversationId("inspection-conversation")
