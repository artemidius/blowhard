@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsAction
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsUiState

@Composable
internal fun ConnectionSettingsScreen(
    state: ConnectionSettingsUiState,
    onAction: (ConnectionSettingsAction) -> Unit,
    onBack: (() -> Unit)?,
) {
    var showGenerationSettings by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeader(state, onBack)
            state.notice?.let { SettingsNotice(it, onAction) }
            CredentialSettingsSection(state.settings, onAction)
            ModelSettingsSection(state.settings, onAction)
            TextButton(onClick = { showGenerationSettings = !showGenerationSettings }) {
                Text(if (showGenerationSettings) "Hide generation settings −" else "Generation settings +")
            }
            if (showGenerationSettings) GenerationParametersSection(state.settings, onAction)
            LiveReadinessSection(state.settings)
        }
    }
}

@Composable
private fun SettingsHeader(state: ConnectionSettingsUiState, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
        Column {
            val title = when {
                onBack != null -> "Provider settings"
                state.settings.credentialStatus == CredentialStatus.UNKNOWN -> "Checking your connection"
                state.settings.credentialStatus == CredentialStatus.MISSING -> "Connect to Mistral"
                state.settings.credentialStatus == CredentialStatus.UNAVAILABLE -> "Secure storage unavailable"
                state.settings.connectionStatus == ConnectionStatus.TESTING -> "Verifying your connection"
                else -> "Connection needs attention"
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                when (state.settings.credentialStatus) {
                    CredentialStatus.CONFIGURED ->
                        "Your saved key must pass verification before chat opens. Messages you send are processed by Mistral."

                    CredentialStatus.UNAVAILABLE ->
                        "This platform does not yet provide secure API key storage."

                    else -> "Add a Mistral API key to start. Messages you send are processed by Mistral."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsNotice(message: String, onAction: (ConnectionSettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onAction(ConnectionSettingsAction.NoticeDismissed) }) {
            Text("Dismiss")
        }
    }
}
