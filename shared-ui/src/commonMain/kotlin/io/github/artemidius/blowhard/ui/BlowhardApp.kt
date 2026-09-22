package io.github.artemidius.blowhard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter

/** Root presentation entry point shared by Blowhard clients. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
public fun BlowhardApp(
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
) {
    val conversationState by conversationPresenter.state.collectAsState()
    val settingsState by connectionSettingsPresenter.state.collectAsState()
    var destination by remember { mutableStateOf(WorkspaceDestination.CONVERSATION) }
    MaterialTheme(colorScheme = BlowhardColors) {
        when (destination) {
            WorkspaceDestination.CONVERSATION -> ConversationScreen(
                state = conversationState,
                onAction = conversationPresenter::dispatch,
                onOpenSettings = { destination = WorkspaceDestination.SETTINGS },
            )

            WorkspaceDestination.SETTINGS -> ConnectionSettingsScreen(
                state = settingsState,
                onAction = connectionSettingsPresenter::dispatch,
                onBack = { destination = WorkspaceDestination.CONVERSATION },
            )
        }
    }
}

private enum class WorkspaceDestination {
    CONVERSATION,
    SETTINGS,
}

private val BlowhardColors = lightColorScheme(
    primary = Color(0xFF315C72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECF8),
    onPrimaryContainer = Color(0xFF102F3E),
    secondary = Color(0xFF7A4F01),
    secondaryContainer = Color(0xFFFFDEA4),
    surface = Color(0xFFFAFCFD),
    surfaceVariant = Color(0xFFE7EEF1),
    background = Color(0xFFF3F7F8),
)
