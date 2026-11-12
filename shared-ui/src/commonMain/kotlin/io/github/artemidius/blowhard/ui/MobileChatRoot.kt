@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.runtime.Composable
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsUiState
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorUiState

@Composable
internal fun MobileChatRoot(
    navigation: MobileNavigationState,
    conversationState: ConversationUiState,
    settingsState: ConnectionSettingsUiState,
    inspectorState: RunInspectorUiState,
    documentLibraryState: DocumentLibraryUiState,
    conversationPresenter: ConversationPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    when (navigation.current) {
        MobileDestination.CONVERSATION -> MobileConversationDestination(
            navigation,
            conversationState,
            settingsState,
            documentLibraryState,
            conversationPresenter,
            runInspectorPresenter,
            navigate,
        )

        MobileDestination.CONVERSATION_LIST -> MobileConversationListDestination(
            navigation,
            conversationState,
            conversationPresenter,
            navigate,
        )

        MobileDestination.RUN_INSPECTOR -> MobileInspectorDestination(
            navigation,
            inspectorState,
            runInspectorPresenter,
            navigate,
        )

        else -> error("Unexpected destination for the Chats root")
    }
}

@Composable
private fun MobileConversationListDestination(
    navigation: MobileNavigationState,
    state: ConversationUiState,
    presenter: ConversationPresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    val close = { navigate(navigation.back()) }
    MobileConversationListScreen(
        state = state,
        onNew = {
            presenter.dispatch(ConversationAction.NewConversation)
            close()
        },
        onSelect = { conversationId ->
            presenter.dispatch(ConversationAction.ConversationSelected(conversationId))
            close()
        },
        onBack = close,
    )
}

@Composable
private fun MobileInspectorDestination(
    navigation: MobileNavigationState,
    state: RunInspectorUiState,
    presenter: RunInspectorPresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    RunInspectorScreen(state, presenter::dispatch, onBack = { navigate(navigation.back()) })
}
