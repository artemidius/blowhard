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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenario

@Composable
internal fun ComposerPanel(state: ConversationUiState, onAction: (ConversationAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScenarioSelector(state = state, onAction = onAction)
        OutlinedTextField(
            value = state.composer,
            onValueChange = { onAction(ConversationAction.ComposerChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask the offline fixture anything…") },
            minLines = 2,
            maxLines = 6,
            enabled = !state.selectedConversation.runState.isActive,
        )
        ComposerActions(state = state, onAction = onAction)
    }
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
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
