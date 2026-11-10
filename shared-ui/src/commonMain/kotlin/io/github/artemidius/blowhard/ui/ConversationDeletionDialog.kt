@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ConversationDeletionDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete local conversation?") },
        text = {
            Text(
                "“$title” and its messages and run history will be removed from Blowhard's local database. " +
                    "This does not delete data already sent to Mistral or change the API credential.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete local history") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
