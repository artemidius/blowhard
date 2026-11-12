@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsUiState
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteUiState
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorUiState

@Composable
internal fun MobileWorkspace(
    navigation: MobileNavigationState,
    conversationState: ConversationUiState,
    settingsState: ConnectionSettingsUiState,
    inspectorState: RunInspectorUiState,
    documentLibraryState: DocumentLibraryUiState,
    meetingNoteState: MeetingNoteUiState,
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    if (!settingsState.settings.canUseLiveMode) {
        ConnectionSettingsScreen(settingsState, connectionSettingsPresenter::dispatch, onBack = null)
        return
    }
    Scaffold(
        bottomBar = {
            if (navigation.current.isRoot) {
                MobileNavigationBar(navigation.selectedRoot) { navigate(navigation.selectRoot(it)) }
            }
        },
    ) { padding ->
        MobileDestinationContent(
            navigation,
            conversationState,
            settingsState,
            inspectorState,
            documentLibraryState,
            meetingNoteState,
            conversationPresenter,
            connectionSettingsPresenter,
            runInspectorPresenter,
            documentLibraryPresenter,
            meetingNotePresenter,
            navigate,
            Modifier.padding(padding),
        )
    }
}

@Composable
private fun MobileDestinationContent(
    navigation: MobileNavigationState,
    conversationState: ConversationUiState,
    settingsState: ConnectionSettingsUiState,
    inspectorState: RunInspectorUiState,
    documentLibraryState: DocumentLibraryUiState,
    meetingNoteState: MeetingNoteUiState,
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
    navigate: (MobileNavigationState) -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        when (navigation.selectedRoot) {
            MobileRootDestination.CHATS -> MobileChatRoot(
                navigation,
                conversationState,
                settingsState,
                inspectorState,
                documentLibraryState,
                conversationPresenter,
                runInspectorPresenter,
                navigate,
            )

            MobileRootDestination.DOCUMENTS -> MobileDocumentsRoot(
                navigation,
                documentLibraryState,
                meetingNoteState,
                documentLibraryPresenter,
                meetingNotePresenter,
                navigate,
            )

            MobileRootDestination.SETTINGS -> MobileSettingsDestination(settingsState, connectionSettingsPresenter)
        }
    }
}

@Composable
private fun MobileSettingsDestination(state: ConnectionSettingsUiState, presenter: ConnectionSettingsPresenter) {
    ConnectionSettingsScreen(state, presenter::dispatch, onBack = null)
}
