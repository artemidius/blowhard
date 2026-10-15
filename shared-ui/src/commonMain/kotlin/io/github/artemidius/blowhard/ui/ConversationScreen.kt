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
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun ConversationScreen(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
    onInspectRun: (RunId) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (conversationLayoutFor(maxWidth) == ConversationLayout.WIDE) {
            WideConversationScreen(state, connection, onAction, onOpenSettings, onOpenDocuments, onInspectRun)
        } else {
            ConversationPane(
                state,
                connection,
                onAction,
                onOpenSettings,
                onOpenDocuments,
                onInspectRun,
                compact = true,
            )
        }
    }
    state.pendingDeletionConversation?.let { conversation ->
        ConversationDeletionDialog(
            title = conversation.title,
            onConfirm = { onAction(ConversationAction.DeleteConversationConfirmed) },
            onDismiss = { onAction(ConversationAction.DeleteConversationDismissed) },
        )
    }
}

@Composable
private fun WideConversationScreen(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
    onInspectRun: (RunId) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        ConversationSidebar(
            state = state,
            connection = connection,
            onAction = onAction,
            onOpenSettings = onOpenSettings,
            onOpenDocuments = onOpenDocuments,
            modifier = Modifier.width(SIDEBAR_WIDTH),
        )
        ConversationPane(
            state,
            connection,
            onAction,
            onOpenSettings,
            onOpenDocuments,
            onInspectRun,
            compact = false,
        )
    }
}

private val SIDEBAR_WIDTH = 264.dp
