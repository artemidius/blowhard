package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopDemoRuntimeTest {
    @Test
    fun `real desktop runtime completes the bundled offline conversation`() = runBlocking {
        val runtime = DesktopDemoRuntime.create(testClipboard, FakeDesktopConnectionSettings())
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
    fun `real desktop runtime cancels streaming fixture output`() = runBlocking {
        val runtime = DesktopDemoRuntime.create(testClipboard, FakeDesktopConnectionSettings())
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
        val runtime = DesktopDemoRuntime.create(
            testClipboard,
            FakeDesktopConnectionSettings(),
            closeProviderResources = { closeCalls += 1 },
        )

        runtime.close()
        runtime.close()

        assertEquals(1, closeCalls)
    }
}

private class FakeDesktopConnectionSettings : ConnectionSettings {
    private val mutableState = MutableStateFlow(ConnectionSettingsState())
    override val state: StateFlow<ConnectionSettingsState> = mutableState

    override suspend fun refreshCredentialStatus() = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Failure(
        ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE,
    )

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.NOT_AVAILABLE

    override suspend fun acknowledgeLiveDataDisclosure() = Unit

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.LIVE_NOT_READY
}

private val testClipboard = ClipboardWriter { ClipboardWriteResult.COPIED }
