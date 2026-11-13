@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsAction

@Composable
internal fun CredentialSettingsSection(
    settings: ConnectionSettingsState,
    onAction: (ConnectionSettingsAction) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("API credential", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                settings.credentialStatus.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CredentialInput(settings, onAction)
            ConnectionTestControls(settings, onAction)
        }
    }
}

@Composable
private fun CredentialInput(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
    var credential by remember { mutableStateOf("") }
    val storageAvailable = settings.credentialStatus != CredentialStatus.UNAVAILABLE
    OutlinedTextField(
        value = credential,
        onValueChange = { credential = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = storageAvailable,
        singleLine = true,
        label = {
            Text(if (settings.credentialStatus == CredentialStatus.CONFIGURED) "Replace API key" else "API key")
        },
        visualTransformation = PasswordVisualTransformation(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val submitted = credential
                credential = ""
                onAction(ConnectionSettingsAction.CredentialSubmitted(submitted))
            },
            enabled = storageAvailable && credential.isNotBlank(),
        ) {
            Text(if (settings.credentialStatus == CredentialStatus.CONFIGURED) "Replace" else "Save")
        }
        OutlinedButton(
            onClick = { onAction(ConnectionSettingsAction.CredentialDeleted) },
            enabled = settings.credentialStatus == CredentialStatus.CONFIGURED,
        ) {
            Text("Delete")
        }
    }
}

@Composable
private fun ConnectionTestControls(settings: ConnectionSettingsState, onAction: (ConnectionSettingsAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onAction(ConnectionSettingsAction.ConnectionTested) },
            enabled = settings.credentialStatus == CredentialStatus.CONFIGURED &&
                settings.connectionStatus != ConnectionStatus.TESTING,
        ) {
            Text(if (settings.connectionStatus == ConnectionStatus.TESTING) "Testing…" else "Test connection")
        }
        Text(
            settings.connectionStatus.description(),
            modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CredentialStatus.description(): String = when (this) {
    CredentialStatus.UNKNOWN -> "Checking the platform credential vault…"
    CredentialStatus.MISSING -> "No credential is stored."
    CredentialStatus.CONFIGURED -> "A credential is configured in the platform vault. Its value is never displayed."
    CredentialStatus.UNAVAILABLE -> "Verified secure credential storage is unavailable on this platform."
}

private fun ConnectionStatus.description(): String = when (this) {
    ConnectionStatus.NOT_TESTED -> "Not tested"
    ConnectionStatus.TESTING -> "Verifying with the model catalog"
    ConnectionStatus.READY -> "Verified"
    ConnectionStatus.FAILED -> "Verification failed"
}
