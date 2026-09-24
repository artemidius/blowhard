@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun ConversationScreen(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (conversationLayoutFor(maxWidth) == ConversationLayout.WIDE) {
            Row(Modifier.fillMaxSize()) {
                ConversationSidebar(
                    state = state,
                    connection = connection,
                    onAction = onAction,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.width(SIDEBAR_WIDTH),
                )
                ConversationPane(state, connection, onAction, onOpenSettings, compact = false)
            }
        } else {
            ConversationPane(state, connection, onAction, onOpenSettings, compact = true)
        }
    }
}

private val SIDEBAR_WIDTH = 264.dp
