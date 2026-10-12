@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsAction

@Composable
internal fun ModelSettingsSection(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Verified model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (settings.models.isEmpty()) {
                Text(
                    "Test the connection to discover models with explicit chat-completion support.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(onClick = { expanded = true }) {
                    Text("Model: ${settings.selectedModelId ?: "Choose a model"}  ▾")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    settings.models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.id) },
                            onClick = {
                                expanded = false
                                onAction(ConnectionSettingsAction.ModelSelected(model.id))
                            },
                        )
                    }
                }
                val selected = settings.models.firstOrNull { it.id == settings.selectedModelId }
                Text(
                    selected?.contextWindowTokens?.let { "Documented context window: $it tokens" }
                        ?: "The provider did not report a context-window size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
