@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsAction

@Composable
internal fun LiveReadinessSection(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("External processing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Live requests send message content and the selected model identifier to Mistral. " +
                    "Failures never switch silently to the offline fixture.",
                style = MaterialTheme.typography.bodyMedium,
            )
            DisclosureAcknowledgement(settings, onAction)
            ReadinessStatus(settings)
            ModeSelector(settings, onAction)
            Text(
                "An accepted attempt keeps its selected mode and model even if settings change later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisclosureAcknowledgement(
    settings: ConnectionSettingsState,
    onAction: (ConnectionSettingsAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = settings.liveDataDisclosureAccepted,
            onCheckedChange = { accepted ->
                if (accepted) onAction(ConnectionSettingsAction.LiveDataDisclosureAccepted)
            },
            enabled = !settings.liveDataDisclosureAccepted,
        )
        Text("I understand that live prompts are processed by an external provider.")
    }
}

@Composable
private fun ReadinessStatus(settings: ConnectionSettingsState) {
    Text(
        settings.readinessMessage(),
        style = MaterialTheme.typography.labelMedium,
        color = if (settings.canUseLiveMode) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ModeSelector(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
    Text("MODE FOR NEW ATTEMPTS", style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GenerationMode.entries.forEach { mode ->
            FilterChip(
                selected = settings.mode == mode,
                onClick = { onAction(ConnectionSettingsAction.ModeSelected(mode)) },
                label = { Text(if (mode == GenerationMode.DEMO) "Offline demo" else "Live Mistral") },
            )
        }
    }
}

private fun ConnectionSettingsState.readinessMessage(): String = when {
    mode == GenerationMode.LIVE && canUseLiveMode -> "Live mode is selected for new attempts."

    mode == GenerationMode.LIVE ->
        "Live mode remains selected, but new attempts fail closed until verification is restored."

    canUseLiveMode -> "Configuration is ready for an explicit live-mode selection."

    else -> "Save a credential, verify a compatible model, and acknowledge external processing."
}
