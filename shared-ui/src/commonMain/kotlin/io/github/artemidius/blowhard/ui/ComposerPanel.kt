@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (connection.mode == GenerationMode.DEMO) ScenarioSelector(state = state, onAction = onAction)
        ComposerInput(state, connection, composerFocus, onAction)
        ComposerActions(state = state, onAction = onAction)
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
        label = { Text("Message") },
        placeholder = { Text(connection.composerPlaceholder()) },
        minLines = 2,
        maxLines = 6,
        enabled = !state.selectedConversation.runState.isActive,
    )
}

private fun ConnectionSettingsState.composerPlaceholder(): String = if (mode == GenerationMode.DEMO) {
    "Ask the offline fixture anything…"
} else {
    "Send a prompt to ${selectedModelId ?: "the live provider"}…"
}

@Composable
private fun ScenarioSelector(state: ConversationUiState, onAction: (ConversationAction) -> Unit) {
    Text(
        "DEMO SCENARIO",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAction(ConversationAction.Send) }, enabled = state.canSend) {
            Text("Send")
        }
        OutlinedButton(onClick = { onAction(ConversationAction.Stop) }, enabled = state.canStop) {
            Text("Stop")
        }
        Text(
            state.selectedConversation.runState.displayLabel(),
            modifier = Modifier
                .padding(start = 8.dp, top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
