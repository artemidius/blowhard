package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun `accepted live run keeps its verified model when settings change before execution`() = runTest {
        val settings = FakeRunConnectionSettings(readyLiveState())
        var observedTarget: GenerationTarget? = null
        val runs = controller(
            generation = TextGenerationPort { request ->
                observedTarget = request.target
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        val result = assertIs<StartRunResult.Started>(
            runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello")),
        )
        settings.mutableState.value = ConnectionSettingsState()
        advanceUntilIdle()

        assertEquals(GenerationTarget.Live("verified-model"), result.target)
        assertEquals(result.target, observedTarget)
    }

    @Test
    fun `accepted run keeps its generation parameters when settings change before execution`() = runTest {
        val acceptedParameters = GenerationParameters(maxOutputTokens = 1_024, temperature = 0.2)
        val settings = FakeRunConnectionSettings(
            readyLiveState().copy(generationParameters = acceptedParameters),
        )
        var observedParameters: GenerationParameters? = null
        val runs = controller(
            generation = TextGenerationPort { request ->
                observedParameters = request.parameters
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello"))
        settings.mutableState.value = settings.mutableState.value.copy(
            generationParameters = GenerationParameters(maxOutputTokens = 256, temperature = 0.7),
        )
        advanceUntilIdle()

        assertEquals(acceptedParameters, observedParameters)
    }

    @Test
    fun `stale live selection fails closed before provider execution`() = runTest {
        var executions = 0
        val settings = FakeRunConnectionSettings(
            readyLiveState().copy(connectionStatus = ConnectionStatus.FAILED),
        )
        val runs = controller(
            generation = TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        val result = runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello"))
        advanceUntilIdle()

        assertEquals(
            StartRunRejection.LIVE_CONFIGURATION_NOT_READY,
            assertIs<StartRunResult.Rejected>(result).reason,
        )
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
        connectionSettings: ConnectionSettings? = null,
    ): DefaultConversationRuns = DefaultConversationRuns(
        generation = generation,
        idFactory = SequentialRunIdFactory(),
        clock = IncrementingClock(),
        coroutineContext = StandardTestDispatcher(testScheduler),
        policy = RunExecutionPolicy(maxConcurrentRuns),
        connectionSettings = connectionSettings,
    )
}

private class FakeRunConnectionSettings(initial: ConnectionSettingsState) : ConnectionSettings {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ConnectionSettingsState> = mutableState

    override suspend fun refreshCredentialStatus() = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Failure(
        ConnectionTestFailureKind.UNEXPECTED,
    )

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.UPDATED

    override suspend fun updateGenerationParameters(parameters: GenerationParameters) = Unit

    override suspend fun acknowledgeLiveDataDisclosure() = Unit

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.UPDATED
}

private fun readyLiveState(): ConnectionSettingsState = ConnectionSettingsState(
    mode = GenerationMode.LIVE,
    credentialStatus = CredentialStatus.CONFIGURED,
    connectionStatus = ConnectionStatus.READY,
    models = listOf(SelectableModel("verified-model", 32_000)),
    selectedModelId = "verified-model",
    liveDataDisclosureAccepted = true,
)

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
