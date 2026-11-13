@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryAction
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryNotice

@Composable
internal fun DocumentList(
    documents: List<ImportedDocument>,
    busy: Boolean,
    onAction: (DocumentLibraryAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${documents.size} ${if (documents.size == 1) "DOCUMENT" else "DOCUMENTS"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        items(documents, key = { it.id.value }) { document ->
            DocumentCard(document, busy, onAction)
        }
    }
}

@Composable
private fun DocumentCard(document: ImportedDocument, busy: Boolean, onAction: (DocumentLibraryAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${document.content.description()} · ${document.sizeBytes.readableByteCount()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DocumentCardActions(document, busy, onAction)
        }
    }
}

@Composable
private fun DocumentCardActions(document: ImportedDocument, busy: Boolean, onAction: (DocumentLibraryAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { onAction(DocumentLibraryAction.OpenRequested(document.id)) },
            enabled = !busy,
        ) { Text("View") }
        TextButton(
            onClick = { onAction(DocumentLibraryAction.ProcessRequested(document.id)) },
            enabled = !busy,
        ) { Text("Extract text") }
        TextButton(
            onClick = { onAction(DocumentLibraryAction.DeleteRequested(document.id)) },
            enabled = !busy,
        ) { Text("Delete") }
    }
}

@Composable
internal fun DocumentLibraryLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text("Loading local documents…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyDocumentLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text("Your local context shelf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Import a PDF, PNG, or JPEG. Blowhard validates its structure and resource limits before storing it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DocumentLibraryNoticePanel(
    notice: DocumentLibraryNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(notice.message(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
internal fun DocumentDeletionDialog(document: ImportedDocument, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete local document?") },
        text = {
            Text(
                "“${document.displayName}” and its app-managed bytes will be removed from this device. " +
                    "The original selected file is not changed.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete document") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
