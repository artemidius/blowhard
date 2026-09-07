package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal suspend fun runPackagingSmokeScenario(presenter: ConversationPresenter) {
    presenter.dispatch(ConversationAction.ComposerChanged("Verify the installed application"))
    presenter.dispatch(ConversationAction.Send)
    val completed = withTimeout(PACKAGING_SMOKE_TIMEOUT) {
        presenter.state.first { it.selectedConversation.runState == ConversationRunState.SUCCEEDED }
    }
    check(completed.selectedConversation.messages.last().text.contains("Offline demonstration")) {
        "The installed application did not complete its offline scenario"
    }
}

private val PACKAGING_SMOKE_TIMEOUT = 10.seconds
