@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import io.github.artemidius.blowhard.ui.presentation.ConversationUiModel
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun ConversationSidebar(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberSelectedConversationListState(state)
    Column(
        modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader()
        Button(
            onClick = { onAction(ConversationAction.NewConversation) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("+  New chat")
        }
        Text(
            "YOUR CONVERSATIONS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.conversations, key = { it.id.value }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    selected = conversation.id == state.selectedConversationId,
                    onClick = { onAction(ConversationAction.ConversationSelected(conversation.id)) },
                )
            }
        }
        SidebarStatus(connection, onOpenSettings, onOpenDocuments)
    }
}

@Composable
private fun SidebarStatus(
    connection: ConnectionSettingsState,
    onOpenSettings: () -> Unit,
    onOpenDocuments: () -> Unit,
) {
    OutlinedButton(onClick = onOpenDocuments, modifier = Modifier.fillMaxWidth()) {
        Text("Documents")
    }
    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Settings")
    }
    Text(
        if (connection.mode == GenerationMode.DEMO) {
            "Offline fixture • no API key • no network"
        } else {
            "Mistral · cloud processing"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberSelectedConversationListState(state: ConversationUiState): LazyListState {
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedConversationId) {
        val selectedIndex = state.conversations.indexOfFirst { it.id == state.selectedConversationId }
        if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
    }
    return listState
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlowhardMark()
        Column {
            Text("blowhard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "THINK BEYOND.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConversationItem(conversation: ConversationUiModel, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Open conversation: ${conversation.title}"
                role = Role.Button
                this.selected = selected
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                conversation.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            ConversationMetadata(conversation)
        }
    }
}

@Composable
private fun ConversationMetadata(conversation: ConversationUiModel) {
    if (conversation.runState != ConversationRunState.IDLE && conversation.runState != ConversationRunState.SUCCEEDED) {
        Text(
            conversation.runState.displayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

internal fun ConversationRunState.displayLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }
