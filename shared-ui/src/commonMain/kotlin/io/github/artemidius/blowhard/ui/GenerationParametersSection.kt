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
internal fun GenerationParametersSection(
    settings: ConnectionSettingsState,
    onAction: (ConnectionSettingsAction) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Generation controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Each accepted attempt snapshots these controls. Model default omits the field from the request.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ParameterLabel("MAXIMUM OUTPUT")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OUTPUT_TOKEN_PRESETS) { preset ->
                    FilterChip(
                        selected = settings.generationParameters.maxOutputTokens == preset,
                        onClick = { onAction(ConnectionSettingsAction.MaxOutputTokensSelected(preset)) },
                        label = { Text(preset?.let { "$it tokens" } ?: "Model default") },
                    )
                }
            }
            ParameterLabel("TEMPERATURE")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TEMPERATURE_PRESETS) { preset ->
                    FilterChip(
                        selected = settings.generationParameters.temperature == preset.value,
                        onClick = { onAction(ConnectionSettingsAction.TemperatureSelected(preset.value)) },
                        label = { Text(preset.label) },
                    )
                }
            }
            Text(
                "Temperature is limited to Mistral's documented 0.0–0.7 recommended range.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ParameterLabel(value: String) {
    Text(value, style = MaterialTheme.typography.labelSmall)
}

private data class TemperaturePreset(val value: Double?, val label: String)

private val OUTPUT_TOKEN_PRESETS: List<Int?> = listOf(null, 256, 1_024, 4_096)
private val TEMPERATURE_PRESETS = listOf(
    TemperaturePreset(null, "Model default"),
    TemperaturePreset(0.2, "Focused · 0.2"),
    TemperaturePreset(0.5, "Balanced · 0.5"),
    TemperaturePreset(0.7, "Creative · 0.7"),
)
