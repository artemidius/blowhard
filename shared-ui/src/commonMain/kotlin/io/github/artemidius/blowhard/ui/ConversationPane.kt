@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun ConversationPane(
    state: ConversationUiState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    compact: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        if (compact) CompactConversationNavigation(state, onAction, onOpenSettings)
        ConversationHeader(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MessageList(
            messages = state.selectedConversation.messages,
            onAction = onAction,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        state.notice?.let { notice ->
            NoticePanel(
                notice = notice,
                onDismiss = { onAction(ConversationAction.NoticeDismissed) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        ComposerPanel(state = state, onAction = onAction)
    }
}

@Composable
private fun ConversationHeader(state: ConversationUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                state.selectedConversation.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Offline deterministic provider",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DemoBadge()
    }
}

@Composable
private fun CompactConversationNavigation(
    state: ConversationUiState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
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
