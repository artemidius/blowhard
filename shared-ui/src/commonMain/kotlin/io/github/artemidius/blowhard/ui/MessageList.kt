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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.ChatMessageUi
import io.github.artemidius.blowhard.ui.presentation.ChatRole
import io.github.artemidius.blowhard.ui.presentation.MessageDelivery

@Composable
internal fun MessageList(messages: List<ChatMessageUi>, modifier: Modifier = Modifier) {
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
        items(messages, key = { it.id }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageUi) {
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
                    if (isUser) "YOU" else "FIXTURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (message.text.isBlank() && message.delivery == MessageDelivery.STREAMING) {
                    Text("Waiting for output…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SafeMarkdownText(message.text)
                }
                if (message.delivery != MessageDelivery.COMPLETE) {
                    Text(
                        message.delivery.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offline conversation demo", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose a scenario, send a message, and inspect the deterministic lifecycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MESSAGE_WIDTH_FRACTION = 0.82f
