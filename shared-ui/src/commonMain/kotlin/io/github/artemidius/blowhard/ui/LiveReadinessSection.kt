@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
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
            Text(
                if (settings.canUseLiveMode) {
                    "Configuration is ready for an explicit live-mode selection."
                } else {
                    "Save a credential, verify a compatible model, and acknowledge external processing."
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (settings.canUseLiveMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                "This settings step does not reroute the current offline conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
