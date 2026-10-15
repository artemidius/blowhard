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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryAction
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState

@Composable
internal fun DocumentLibraryScreen(
    state: DocumentLibraryUiState,
    onAction: (DocumentLibraryAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DocumentLibraryHeader(state, onAction, onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isBusy && state.documents.isEmpty() -> DocumentLibraryLoading()
                state.documents.isEmpty() -> EmptyDocumentLibrary()
                else -> DocumentList(state.documents, state.isBusy, onAction)
            }
            state.notice?.let { notice ->
                DocumentLibraryNoticePanel(
                    notice = notice,
                    onDismiss = { onAction(DocumentLibraryAction.NoticeDismissed) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
    state.pendingDeletion?.let { document ->
        DocumentDeletionDialog(
            document = document,
            onConfirm = { onAction(DocumentLibraryAction.DeleteConfirmed) },
            onDismiss = { onAction(DocumentLibraryAction.DeleteDismissed) },
        )
    }
}

@Composable
private fun DocumentLibraryHeader(
    state: DocumentLibraryUiState,
    onAction: (DocumentLibraryAction) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Column(Modifier.weight(1f)) {
            Text("Local documents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Inspected locally · app-managed storage · never sent automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { onAction(DocumentLibraryAction.ImportRequested) },
            enabled = !state.isBusy,
        ) {
            Text(if (state.isBusy) "Working…" else "Import document")
        }
    }
}
