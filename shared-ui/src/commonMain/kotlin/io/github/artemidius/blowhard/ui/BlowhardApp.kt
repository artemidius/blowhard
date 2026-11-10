package io.github.artemidius.blowhard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorAction
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter

/** Root presentation entry point shared by Blowhard clients. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
public fun BlowhardApp(
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
    runInspectorPresenter: RunInspectorPresenter,
) {
    val conversationState by conversationPresenter.state.collectAsState()
    val settingsState by connectionSettingsPresenter.state.collectAsState()
    val inspectorState by runInspectorPresenter.state.collectAsState()
    var destination by remember { mutableStateOf(WorkspaceDestination.CONVERSATION) }
    MaterialTheme(colorScheme = BlowhardColors, shapes = BlowhardShapes, typography = BlowhardTypography) {
        Surface {
            when (destination) {
                WorkspaceDestination.CONVERSATION -> ConversationScreen(
                    state = conversationState,
                    connection = settingsState.settings,
                    onAction = conversationPresenter::dispatch,
                    onOpenSettings = { destination = WorkspaceDestination.SETTINGS },
                    onInspectRun = { runId ->
                        runInspectorPresenter.dispatch(RunInspectorAction.Open(runId))
                        destination = WorkspaceDestination.INSPECTOR
                    },
                )

                WorkspaceDestination.SETTINGS -> ConnectionSettingsScreen(
                    state = settingsState,
                    onAction = connectionSettingsPresenter::dispatch,
                    onBack = { destination = WorkspaceDestination.CONVERSATION },
                )

                WorkspaceDestination.INSPECTOR -> RunInspectorScreen(
                    state = inspectorState,
                    onAction = runInspectorPresenter::dispatch,
                    onBack = { destination = WorkspaceDestination.CONVERSATION },
                )
            }
        }
    }
}

private enum class WorkspaceDestination {
    CONVERSATION,
    SETTINGS,
    INSPECTOR,
}
