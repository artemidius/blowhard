@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun ConversationPane(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    availableImages: List<ImportedDocument>,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
    onInspectRun: (RunId) -> Unit,
    compact: Boolean,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (compact) CompactConversationNavigation(state, onAction, onOpenSettings, onOpenDocuments)
        ConversationHeader(state, connection, onAction, onOpenSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MessageList(
            messages = state.selectedConversation.messages,
            regenerationEnabled = !state.selectedConversation.runState.isActive,
            onAction = onAction,
            onInspectRun = onInspectRun,
            modifier = Modifier.weight(1f).widthIn(max = 920.dp).fillMaxWidth(),
        )
        state.notice?.let { notice ->
            NoticePanel(
                notice = notice,
                onDismiss = { onAction(ConversationAction.NoticeDismissed) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        ComposerPanel(
            state = state,
            connection = connection,
            availableImages = availableImages,
            onOpenDocuments = onOpenDocuments,
            onAction = onAction,
        )
    }
}

@Composable
private fun ConversationHeader(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                state.selectedConversation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Next message • ${connection.currentTargetDescription()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                onAction(ConversationAction.DeleteConversationRequested(state.selectedConversationId))
            },
            enabled = !state.selectedConversation.runState.isActive,
        ) {
            Text("Delete")
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.widthIn(max = 240.dp)) {
            Text(
                connection.selectedModelId?.let { "Model: $it" } ?: "Choose model",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ConnectionSettingsState.currentTargetDescription(): String = when (mode) {
    GenerationMode.DEMO -> "Local demo · no API key needed"
    GenerationMode.LIVE -> selectedModelId?.let { "Live Mistral • $it" } ?: "Live configuration unavailable"
}

@Composable
private fun CompactConversationNavigation(
    state: ConversationUiState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            TextButton(onClick = { onAction(ConversationAction.NewConversation) }) {
                Text("+ New")
            }
        }
        item {
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        item {
            TextButton(onClick = onOpenDocuments) { Text("Documents") }
        }
        items(state.conversations, key = { it.id.value }) { conversation ->
            TextButton(onClick = { onAction(ConversationAction.ConversationSelected(conversation.id)) }) {
                Text(
                    conversation.title,
                    color = if (conversation.id == state.selectedConversationId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}
