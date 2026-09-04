package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
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
        val runtime = DesktopDemoRuntime.create()
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
        val runtime = DesktopDemoRuntime.create()
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
}
