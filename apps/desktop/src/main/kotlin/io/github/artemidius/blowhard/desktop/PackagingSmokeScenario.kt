package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal suspend fun runPackagingSmokeScenario(presenter: ConversationPresenter) {
    val previousMessageCount = presenter.state.value.selectedConversation.messages.size
    presenter.dispatch(ConversationAction.ComposerChanged("Verify the installed application"))
    presenter.dispatch(ConversationAction.Send)
    withTimeout(PACKAGING_SMOKE_TIMEOUT) {
        presenter.state.first { state ->
            val conversation = state.selectedConversation
            conversation.runState == ConversationRunState.SUCCEEDED &&
                conversation.messages.size >= previousMessageCount + EXPECTED_NEW_MESSAGE_COUNT &&
                conversation.messages.last().text.contains(EXPECTED_OFFLINE_HEADING)
        }
    }
}

private const val EXPECTED_NEW_MESSAGE_COUNT = 2
private const val EXPECTED_OFFLINE_HEADING = "Offline demonstration"
private val PACKAGING_SMOKE_TIMEOUT = 10.seconds
