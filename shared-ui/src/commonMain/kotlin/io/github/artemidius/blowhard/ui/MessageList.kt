@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.ui.presentation.ChatMessageUi
import io.github.artemidius.blowhard.ui.presentation.ChatRole
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.MessageDelivery

@Composable
internal fun MessageList(
    messages: List<ChatMessageUi>,
    regenerationEnabled: Boolean,
    onAction: (ConversationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        EmptyConversation(modifier)
        return
    }
    val listState = rememberLazyListState()
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLength) {
        listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 24.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message, regenerationEnabled, onAction)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUi,
    regenerationEnabled: Boolean,
    onAction: (ConversationAction) -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(MESSAGE_WIDTH_FRACTION),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = if (isUser) 0.dp else 1.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isUser) "YOU" else message.target.providerLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (message.text.isBlank() && message.delivery == MessageDelivery.STREAMING) {
                    Text("Waiting for output…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SafeMarkdownText(message.text)
                }
                MessageFooter(message, isUser, regenerationEnabled, onAction)
            }
        }
    }
}

@Composable
private fun MessageFooter(
    message: ChatMessageUi,
    isUser: Boolean,
    regenerationEnabled: Boolean,
    onAction: (ConversationAction) -> Unit,
) {
    message.attemptId?.let { attemptId ->
        Text(
            "Attempt ${attemptId.value}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (message.delivery != MessageDelivery.COMPLETE) {
        Text(
            message.delivery.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (message.text.isNotBlank()) {
        TextButton(onClick = { onAction(ConversationAction.CopyMessage(message.id)) }) {
            Text("Copy ${if (isUser) "your message" else "response"}")
        }
    }
    if (!isUser && message.canRegenerate) {
        TextButton(
            onClick = { onAction(ConversationAction.Regenerate(message.id)) },
            enabled = regenerationEnabled,
        ) {
            Text("Regenerate with current mode")
        }
    }
}

private fun GenerationTarget?.providerLabel(): String = when (this) {
    null, GenerationTarget.Demo -> "FIXTURE"
    is GenerationTarget.Live -> "MISTRAL • $modelId"
}

@Composable
private fun EmptyConversation(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Start a conversation", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose an explicit generation mode, send a message, and inspect the shared lifecycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MESSAGE_WIDTH_FRACTION = 0.82f
