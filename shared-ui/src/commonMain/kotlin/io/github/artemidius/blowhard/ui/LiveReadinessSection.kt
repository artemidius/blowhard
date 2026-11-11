@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState

@Composable
internal fun LiveReadinessSection(settings: ConnectionSettingsState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("External processing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Live requests send message content, any explicitly attached image, and the selected model " +
                    "identifier to Mistral. The connection must be verified before you can send a message.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ReadinessStatus(settings)
            Text(
                "An accepted attempt keeps its selected model even if settings change later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

private fun ConnectionSettingsState.readinessMessage(): String = when {
    canUseLiveMode -> "Ready to chat with Mistral."
    else -> "Save an API key and verify the connection to continue."
}
