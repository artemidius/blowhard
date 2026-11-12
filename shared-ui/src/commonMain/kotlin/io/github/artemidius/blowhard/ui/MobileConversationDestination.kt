@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.runtime.Composable
import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsUiState
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.RunInspectorAction
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter

@Composable
internal fun MobileConversationDestination(
    navigation: MobileNavigationState,
    conversationState: ConversationUiState,
    settingsState: ConnectionSettingsUiState,
    documentLibraryState: DocumentLibraryUiState,
    conversationPresenter: ConversationPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    ConversationScreen(
        state = conversationState,
        connection = settingsState.settings,
        availableImages = documentLibraryState.documents.filter {
            it.content is DocumentContentDescriptor.Image
        },
        onAction = conversationPresenter::dispatch,
        onOpenSettings = { navigate(navigation.selectRoot(MobileRootDestination.SETTINGS)) },
        onOpenDocuments = { navigate(navigation.selectRoot(MobileRootDestination.DOCUMENTS)) },
        onInspectRun = { runId ->
            runInspectorPresenter.dispatch(RunInspectorAction.Open(runId))
            navigate(navigation.open(MobileDestination.RUN_INSPECTOR))
        },
    )
}
