@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenario

@Composable
internal fun ComposerPanel(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    onAction: (ConversationAction) -> Unit,
) {
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(state.selectedConversationId, state.selectedConversation.runState.isActive) {
        if (!state.selectedConversation.runState.isActive) composerFocus.requestFocus()
    }
    Column(
        modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ComposerInput(state, connection, composerFocus, onAction)
        ComposerActions(state = state, onAction = onAction)
        if (connection.mode == GenerationMode.DEMO) ScenarioSelector(state = state, onAction = onAction)
    }
}

@Composable
private fun ComposerInput(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    focusRequester: FocusRequester,
    onAction: (ConversationAction) -> Unit,
) {
    OutlinedTextField(
        value = state.composer,
        onValueChange = { onAction(ConversationAction.ComposerChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val sendShortcut = event.type == KeyEventType.KeyDown &&
                    event.key == Key.Enter &&
                    (event.isCtrlPressed || event.isMetaPressed)
                if (sendShortcut && state.canSend) {
                    onAction(ConversationAction.Send)
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        placeholder = { Text(connection.composerPlaceholder()) },
        minLines = 2,
        maxLines = 6,
        enabled = !state.selectedConversation.runState.isActive,
    )
}

private fun ConnectionSettingsState.composerPlaceholder(): String = if (mode == GenerationMode.DEMO) {
    "Try a message in the offline demo…"
} else {
    "Send a prompt to ${selectedModelId ?: "the live provider"}…"
}

@Composable
private fun ScenarioSelector(state: ConversationUiState, onAction: (ConversationAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            "Demo scenario · ${state.scenario.label}  ${if (expanded) "−" else "+"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!expanded) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DemoScenario.entries) { scenario ->
            FilterChip(
                selected = state.scenario == scenario,
                onClick = { onAction(ConversationAction.ScenarioChanged(scenario)) },
                label = { Text(scenario.label) },
            )
        }
    }
    Text(
        state.scenario.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ComposerActions(state: ConversationUiState, onAction: (ConversationAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (state.selectedConversation.runState.isActive) {
                state.selectedConversation.runState.displayLabel()
            } else {
                "⌘ / Ctrl + Enter to send"
            },
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (state.canStop) {
            OutlinedButton(onClick = { onAction(ConversationAction.Stop) }) { Text("Stop") }
        } else {
            Button(onClick = {
                onAction(ConversationAction.Send)
            }, enabled = state.canSend, shape = MaterialTheme.shapes.medium) {
                Text("Send ↑")
            }
        }
    }
}
