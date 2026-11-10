package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureTextGenerationPort
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopRuntimeTest {
    @Test
    fun `real desktop runtime completes the bundled offline conversation`() = runBlocking {
        val runtime = createDesktopRuntime()
        try {
            runtime.presenter.dispatch(ConversationAction.ComposerChanged("Show the demo"))
            runtime.presenter.dispatch(ConversationAction.Send)

            val state = withTimeout(5.seconds) {
                runtime.presenter.state.first { it.selectedConversation.runState == ConversationRunState.SUCCEEDED }
            }
            assertTrue(state.selectedConversation.messages.last().text.contains("Offline demonstration"))
            assertEquals(2, state.selectedConversation.messages.size)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `real desktop runtime regenerates through a fresh application attempt`() = runBlocking {
        val runtime = createDesktopRuntime()
        try {
            runtime.presenter.dispatch(ConversationAction.ComposerChanged("Regenerate the demo"))
            runtime.presenter.dispatch(ConversationAction.Send)
            val first = withTimeout(5.seconds) {
                runtime.presenter.state.first { it.selectedConversation.runState == ConversationRunState.SUCCEEDED }
            }
            val firstMessages = first.selectedConversation.messages
            val firstAttempt = firstMessages.last().attemptId

            runtime.presenter.dispatch(ConversationAction.Regenerate(firstMessages.last().id))
            val second = withTimeout(5.seconds) {
                runtime.presenter.state.first {
                    it.selectedConversation.runState == ConversationRunState.SUCCEEDED &&
                        it.selectedConversation.messages.size == 4
                }
            }
            val messages = second.selectedConversation.messages

            assertEquals(firstMessages[1], messages[1])
            assertEquals(messages[0].text, messages[2].text)
            assertEquals(firstAttempt, messages[0].attemptId)
            assertEquals(messages[2].attemptId, messages[3].attemptId)
            assertTrue(messages[2].attemptId != firstAttempt)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `separate desktop runtimes issue distinct attempt identities`() = runBlocking {
        val first = createDesktopRuntime()
        val second = createDesktopRuntime()
        try {
            val firstAttempt = first.completeAttempt("First runtime")
            val secondAttempt = second.completeAttempt("Second runtime")

            assertTrue(firstAttempt != secondAttempt)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `real desktop runtime cancels streaming fixture output`() = runBlocking {
        val runtime = createDesktopRuntime()
        try {
            runtime.presenter.dispatch(ConversationAction.ComposerChanged("Cancel the demo"))
            runtime.presenter.dispatch(ConversationAction.Send)
            withTimeout(5.seconds) {
                runtime.presenter.state.first { it.selectedConversation.runState == ConversationRunState.STREAMING }
            }

            runtime.presenter.dispatch(ConversationAction.Stop)
            val state = withTimeout(5.seconds) {
                runtime.presenter.state.first { it.selectedConversation.runState == ConversationRunState.CANCELLED }
            }
            assertTrue(state.selectedConversation.messages.last().text.isNotBlank())
            runtime.close()
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `runtime closes provider resources exactly once`() {
        var closeCalls = 0
        var historyCloseCalls = 0
        val runtime = createDesktopRuntime(
            closeHistoryResources = { historyCloseCalls += 1 },
            closeProviderResources = { closeCalls += 1 },
        )

        runtime.close()
        runtime.close()

        assertEquals(1, closeCalls)
        assertEquals(1, historyCloseCalls)
    }

    @Test
    fun `provider resources close when history closure fails`() {
        var providerCloseCalls = 0
        val runtime = createDesktopRuntime(
            closeHistoryResources = { error("history close failure") },
            closeProviderResources = { providerCloseCalls += 1 },
        )

        assertFailsWith<IllegalStateException> { runtime.close() }
        assertEquals(1, providerCloseCalls)
        runtime.close()
        assertEquals(1, providerCloseCalls)
    }

    @Test
    fun `restored startup state does not invoke generation`() {
        var generationCalls = 0
        val initialState = emptyDesktopConversationState().copy(
            conversations = listOf(
                io.github.artemidius.blowhard.ui.presentation.ConversationUiModel(
                    id = io.github.artemidius.blowhard.core.api.ConversationId("stored"),
                    title = "Stored history",
                ),
            ),
            selectedConversationId = io.github.artemidius.blowhard.core.api.ConversationId("stored"),
        )
        val runtime = DesktopRuntime.create(
            clipboardWriter = testClipboard,
            connectionSettings = FakeDesktopConnectionSettings(),
            generation = TextGenerationPort {
                generationCalls += 1
                flowOf(GenerationEvent.Completed)
            },
            scenarioSelection = { _, _ -> },
            initialState = initialState,
        )

        assertEquals(initialState, runtime.presenter.state.value)
        assertEquals(0, generationCalls)
        runtime.close()
    }

    @Test
    fun `runtime sends a verified live target through the shared conversation workflow`() = runBlocking {
        var target: GenerationTarget? = null
        val runtime = DesktopRuntime.create(
            clipboardWriter = testClipboard,
            connectionSettings = FakeDesktopConnectionSettings(readyLiveState()),
            generation = TextGenerationPort { request ->
                target = request.target
                flowOf(GenerationEvent.TextDelta("Live response"), GenerationEvent.Completed)
            },
            scenarioSelection = { _, _ -> },
        )
        try {
            runtime.presenter.dispatch(ConversationAction.ComposerChanged("Use Mistral"))
            runtime.presenter.dispatch(ConversationAction.Send)

            val state = withTimeout(5.seconds) {
                runtime.presenter.state.first { it.selectedConversation.runState == ConversationRunState.SUCCEEDED }
            }
            assertEquals(GenerationTarget.Live("verified-model"), target)
            assertEquals(target, state.selectedConversation.latestTarget)
            assertEquals("Live response", state.selectedConversation.messages.last().text)
        } finally {
            runtime.close()
        }
    }
}

private suspend fun DesktopRuntime.completeAttempt(prompt: String): RunId? {
    presenter.dispatch(ConversationAction.ComposerChanged(prompt))
    presenter.dispatch(ConversationAction.Send)
    val state = withTimeout(5.seconds) {
        presenter.state.first { it.selectedConversation.runState == ConversationRunState.SUCCEEDED }
    }
    return state.selectedConversation.messages.last().attemptId
}

private fun createDesktopRuntime(
    closeHistoryResources: () -> Unit = {},
    closeProviderResources: () -> Unit = {},
): DesktopRuntime {
    val scenarios = FixtureScenarioRouter()
    return DesktopRuntime.create(
        clipboardWriter = testClipboard,
        connectionSettings = FakeDesktopConnectionSettings(),
        generation = FixtureTextGenerationPort(scenarios::scenarioFor),
        scenarioSelection = scenarios,
        closeHistoryResources = closeHistoryResources,
        closeProviderResources = closeProviderResources,
    )
}

private class FakeDesktopConnectionSettings(initialState: ConnectionSettingsState = ConnectionSettingsState()) :
    ConnectionSettings {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<ConnectionSettingsState> = mutableState

    override suspend fun refreshCredentialStatus() = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Failure(
        ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE,
    )

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.NOT_AVAILABLE

    override suspend fun updateGenerationParameters(parameters: GenerationParameters) = Unit

    override suspend fun acknowledgeLiveDataDisclosure() = Unit

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.LIVE_NOT_READY
}

private fun readyLiveState(): ConnectionSettingsState = ConnectionSettingsState(
    mode = GenerationMode.LIVE,
    credentialStatus = CredentialStatus.CONFIGURED,
    connectionStatus = ConnectionStatus.READY,
    models = listOf(SelectableModel("verified-model", 32_000)),
    selectedModelId = "verified-model",
    liveDataDisclosureAccepted = true,
)

private val testClipboard = ClipboardWriter { ClipboardWriteResult.COPIED }
