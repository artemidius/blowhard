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
    onGenerateMeetingNote: (io.github.artemidius.blowhard.core.api.DocumentId) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DocumentLibraryHeader(state, onAction, onGenerateMeetingNote, onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.selectedDocument != null -> DocumentWorkbench(state, onAction)
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
    onGenerateMeetingNote: (io.github.artemidius.blowhard.core.api.DocumentId) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = {
                if (state.selectedDocument == null) onBack() else onAction(DocumentLibraryAction.WorkbenchClosed)
            },
        ) { Text("Back") }
        DocumentLibraryTitle(state, Modifier.weight(1f))
        state.selectedDocument?.takeIf { state.extraction != null }?.let { document ->
            TextButton(
                onClick = { onGenerateMeetingNote(document.id) },
                enabled = !state.isBusy,
            ) { Text("Generate meeting note") }
        }
        Button(
            onClick = {
                state.selectedDocument?.let { onAction(DocumentLibraryAction.ProcessRequested(it.id)) }
                    ?: onAction(DocumentLibraryAction.ImportRequested)
            },
            enabled = !state.isBusy,
        ) {
            Text(
                when {
                    state.isBusy -> "Working…"
                    state.selectedDocument != null && state.extraction != null -> "Extract again"
                    state.selectedDocument != null -> "Extract with Mistral"
                    else -> "Import document"
                },
            )
        }
    }
}

@Composable
private fun DocumentLibraryTitle(state: DocumentLibraryUiState, modifier: Modifier) {
    Column(modifier) {
        Text(
            state.selectedDocument?.displayName ?: "Local documents",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (state.selectedDocument == null) {
                "Inspected locally · app-managed storage · never sent automatically"
            } else {
                "Source stays local until you explicitly request extraction"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
