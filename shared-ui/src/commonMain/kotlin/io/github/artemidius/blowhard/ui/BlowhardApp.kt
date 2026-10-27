@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsUiState
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteAction
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteUiState
import io.github.artemidius.blowhard.ui.presentation.RunInspectorAction
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorUiState

/** Root presentation entry point shared by Blowhard clients. */
@Composable
public fun BlowhardApp(
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
    requiresConnection: Boolean = false,
) {
    val conversationState by conversationPresenter.state.collectAsState()
    val settingsState by connectionSettingsPresenter.state.collectAsState()
    val inspectorState by runInspectorPresenter.state.collectAsState()
    val documentLibraryState by documentLibraryPresenter.state.collectAsState()
    val meetingNoteState by meetingNotePresenter.state.collectAsState()
    var destination by remember { mutableStateOf(WorkspaceDestination.CONVERSATION) }
    MaterialTheme(colorScheme = BlowhardColors, shapes = BlowhardShapes, typography = BlowhardTypography) {
        Surface {
            WorkspaceContent(
                destination = destination,
                requiresConnection = requiresConnection,
                conversationState = conversationState,
                settingsState = settingsState,
                inspectorState = inspectorState,
                documentLibraryState = documentLibraryState,
                meetingNoteState = meetingNoteState,
                conversationPresenter = conversationPresenter,
                connectionSettingsPresenter = connectionSettingsPresenter,
                runInspectorPresenter = runInspectorPresenter,
                documentLibraryPresenter = documentLibraryPresenter,
                meetingNotePresenter = meetingNotePresenter,
                navigate = { destination = it },
            )
        }
    }
}

/** Renders the provider connection workflow for a client that has not assembled the full workspace yet. */
@Composable
public fun BlowhardConnectionApp(connectionSettingsPresenter: ConnectionSettingsPresenter) {
    val settingsState by connectionSettingsPresenter.state.collectAsState()
    MaterialTheme(colorScheme = BlowhardColors, shapes = BlowhardShapes, typography = BlowhardTypography) {
        Surface {
            ConnectionSettingsScreen(
                state = settingsState,
                onAction = connectionSettingsPresenter::dispatch,
                onBack = null,
            )
        }
    }
}

@Composable
private fun WorkspaceContent(
    destination: WorkspaceDestination,
    requiresConnection: Boolean,
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
    navigate: (WorkspaceDestination) -> Unit,
) {
    if (requiresConnection && !settingsState.settings.canUseLiveMode) {
        ConnectionSettingsScreen(settingsState, connectionSettingsPresenter::dispatch, onBack = null)
        return
    }
    WorkspaceDestinationContent(
        destination,
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
    )
}

@Composable
private fun WorkspaceDestinationContent(
    destination: WorkspaceDestination,
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
    navigate: (WorkspaceDestination) -> Unit,
) {
    when (destination) {
        WorkspaceDestination.CONVERSATION -> ConversationScreen(
            state = conversationState,
            connection = settingsState.settings,
            availableImages = documentLibraryState.documents.filter {
                it.content is DocumentContentDescriptor.Image
            },
            onAction = conversationPresenter::dispatch,
            onOpenSettings = { navigate(WorkspaceDestination.SETTINGS) },
            onOpenDocuments = { navigate(WorkspaceDestination.DOCUMENTS) },
            onInspectRun = { runId ->
                runInspectorPresenter.dispatch(RunInspectorAction.Open(runId))
                navigate(WorkspaceDestination.INSPECTOR)
            },
        )

        WorkspaceDestination.SETTINGS -> ConnectionSettingsScreen(
            settingsState,
            connectionSettingsPresenter::dispatch,
            onBack = { navigate(WorkspaceDestination.CONVERSATION) },
        )

        WorkspaceDestination.INSPECTOR -> RunInspectorScreen(
            inspectorState,
            runInspectorPresenter::dispatch,
            onBack = { navigate(WorkspaceDestination.CONVERSATION) },
        )

        WorkspaceDestination.DOCUMENTS -> DocumentDestination(
            documentLibraryState,
            documentLibraryPresenter,
            meetingNotePresenter,
            navigate,
        )

        WorkspaceDestination.MEETING_NOTE -> MeetingNoteScreen(
            meetingNoteState,
            meetingNotePresenter::dispatch,
            onBack = { navigate(WorkspaceDestination.DOCUMENTS) },
        )
    }
}

@Composable
private fun DocumentDestination(
    state: DocumentLibraryUiState,
    documents: DocumentLibraryPresenter,
    notes: MeetingNotePresenter,
    navigate: (WorkspaceDestination) -> Unit,
) {
    DocumentLibraryScreen(
        state,
        documents::dispatch,
        onGenerateMeetingNote = { sourceId ->
            notes.dispatch(MeetingNoteAction.Generate(sourceId))
            navigate(WorkspaceDestination.MEETING_NOTE)
        },
        onBack = { navigate(WorkspaceDestination.CONVERSATION) },
    )
}

private enum class WorkspaceDestination {
    CONVERSATION,
    SETTINGS,
    INSPECTOR,
    DOCUMENTS,
    MEETING_NOTE,
}
