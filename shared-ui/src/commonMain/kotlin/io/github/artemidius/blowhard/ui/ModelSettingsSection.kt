@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsAction

@Composable
internal fun ModelSettingsSection(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(settings.models, key = { it.id }) { model ->
                        FilterChip(
                            selected = settings.selectedModelId == model.id,
                            onClick = { onAction(ConnectionSettingsAction.ModelSelected(model.id)) },
                            label = { Text(model.id) },
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
