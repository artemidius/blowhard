@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun MobileConversationScreen(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    availableImages: List<ImportedDocument>,
    onAction: (ConversationAction) -> Unit,
    onOpenConversations: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
    onInspectRun: (RunId) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MobileConversationHeader(state, connection, onAction, onOpenConversations, onOpenSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MessageList(
            messages = state.selectedConversation.messages,
            regenerationEnabled = !state.selectedConversation.runState.isActive,
            onAction = onAction,
            onInspectRun = onInspectRun,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        state.notice?.let { notice ->
            NoticePanel(
                notice = notice,
                onDismiss = { onAction(ConversationAction.NoticeDismissed) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        ComposerPanel(
            state = state,
            connection = connection,
            availableImages = availableImages,
            onOpenDocuments = onOpenDocuments,
            onAction = onAction,
            modifier = Modifier.imePadding(),
            mobile = true,
        )
    }
    state.pendingDeletionConversation?.let { conversation ->
        ConversationDeletionDialog(
            title = conversation.title,
            onConfirm = { onAction(ConversationAction.DeleteConversationConfirmed) },
            onDismiss = { onAction(ConversationAction.DeleteConversationDismissed) },
        )
    }
}
