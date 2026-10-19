@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryAction
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState

@Composable
internal fun DocumentWorkbench(state: DocumentLibraryUiState, onAction: (DocumentLibraryAction) -> Unit) {
    val document = state.selectedDocument ?: return
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
        if (maxWidth < COMPACT_WORKBENCH_WIDTH) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SourceDocumentPanel(document, state.selectedPageIndex, Modifier.fillMaxWidth().weight(1f))
                ExtractedDocumentPanel(state, onAction, Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SourceDocumentPanel(
                    document,
                    state.selectedPageIndex,
                    Modifier.weight(SOURCE_PANEL_WEIGHT).fillMaxSize(),
                )
                ExtractedDocumentPanel(state, onAction, Modifier.weight(EXTRACTION_PANEL_WEIGHT).fillMaxSize())
            }
        }
    }
}

@Composable
private fun SourceDocumentPanel(document: ImportedDocument, selectedPageIndex: Int, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PanelLabel("LOCAL SOURCE")
            Text(document.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${document.content.description()} · ${document.sizeBytes.readableByteCount()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LocalSourcePreview(selectedPageIndex, Modifier.fillMaxWidth().weight(1f))
            Text(
                "Blowhard sends this source only after an explicit extraction request.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalSourcePreview(selectedPageIndex: Int, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("PAGE ${selectedPageIndex + 1}", style = MaterialTheme.typography.labelLarge)
            Text(
                "Source bytes remain in app-managed local storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExtractedDocumentPanel(
    state: DocumentLibraryUiState,
    onAction: (DocumentLibraryAction) -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelLabel("EXTRACTED MARKDOWN")
            when {
                state.isBusy -> ProcessingDocument()
                state.extraction == null -> EmptyExtraction()
                else -> ExtractionContent(state, onAction)
            }
        }
    }
}

@Composable
private fun ColumnScope.ExtractionContent(state: DocumentLibraryUiState, onAction: (DocumentLibraryAction) -> Unit) {
    val extraction = requireNotNull(state.extraction)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        extraction.pages.forEach { page ->
            TextButton(onClick = { onAction(DocumentLibraryAction.PageSelected(page.index)) }) {
                Text(if (page.index == state.selectedPageIndex) "● ${page.index + 1}" else "Page ${page.index + 1}")
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    val page = extraction.pages[state.selectedPageIndex]
    ExtractedPage(page, Modifier.fillMaxWidth().weight(1f))
    Text(
        "${extraction.processor} · stored locally",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ExtractedPage(page: ExtractedDocumentPage, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (page.markdown.isEmpty()) {
            Text("This page contains no extracted text.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(page.markdown, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ProcessingDocument() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text("Extracting a bounded local copy…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyExtraction() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("No extraction yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Choose Extract with Mistral. Only this explicit action sends the selected source.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelLabel(value: String) {
    Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

private val COMPACT_WORKBENCH_WIDTH = 760.dp
private const val SOURCE_PANEL_WEIGHT = 0.85f
private const val EXTRACTION_PANEL_WEIGHT = 1.15f
