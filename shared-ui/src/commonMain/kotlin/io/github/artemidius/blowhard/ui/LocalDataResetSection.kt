@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.LocalDataResetAction
import io.github.artemidius.blowhard.ui.presentation.LocalDataResetResult
import io.github.artemidius.blowhard.ui.presentation.LocalDataResetUiState

@Composable
internal fun LocalDataResetSection(state: LocalDataResetUiState, onAction: (LocalDataResetAction) -> Unit) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Local data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Erase the saved API key and the complete app database from this device. " +
                "This action cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ResetOutcome(state.outcome, onAction)
        TextButton(
            onClick = { onAction(LocalDataResetAction.Requested) },
            enabled = !state.inProgress,
        ) {
            Text(
                if (state.inProgress) "Erasing local data…" else "Erase local data",
                color = destructiveColor(state.inProgress),
            )
        }
    }
    if (state.confirmationRequested) ResetConfirmation(onAction)
}

@Composable
private fun ResetOutcome(result: LocalDataResetResult?, onAction: (LocalDataResetAction) -> Unit) {
    if (result == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (result) {
                LocalDataResetResult.ERASED -> "Local data erased. Add an API key to reconnect."
                LocalDataResetResult.FAILED -> "Some local data could not be erased. Close the app and try again."
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (result == LocalDataResetResult.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        TextButton(onClick = { onAction(LocalDataResetAction.OutcomeDismissed) }) {
            Text("Dismiss")
        }
    }
}

@Composable
private fun ResetConfirmation(onAction: (LocalDataResetAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(LocalDataResetAction.Cancelled) },
        title = { Text("Erase all local data?") },
        text = {
            Text(
                "Your API key and all app database content on this device will be permanently removed. " +
                    "Cloud provider data is not affected.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(LocalDataResetAction.Confirmed) }) {
                Text("Erase permanently", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(LocalDataResetAction.Cancelled) }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun destructiveColor(disabled: Boolean): Color = if (disabled) {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
} else {
    MaterialTheme.colorScheme.error
}
