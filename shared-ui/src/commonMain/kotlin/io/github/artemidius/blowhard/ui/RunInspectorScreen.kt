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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.ui.presentation.RunInspectorAction
import io.github.artemidius.blowhard.ui.presentation.RunInspectorUiState

@Composable
internal fun RunInspectorScreen(
    state: RunInspectorUiState,
    onAction: (RunInspectorAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        InspectorHeader(state, onAction, onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InspectorContent(state)
    }
}

@Composable
private fun InspectorHeader(state: RunInspectorUiState, onAction: (RunInspectorAction) -> Unit, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Column(Modifier.weight(1f)) {
            Text("Run inspector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Bounded, content-free operational metadata",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onAction(RunInspectorAction.Refresh) },
            enabled = state.runId != null && state !is RunInspectorUiState.Loading,
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun InspectorContent(state: RunInspectorUiState) {
    when (state) {
        RunInspectorUiState.Empty -> InspectorMessage("Select Inspect run from an assistant response.")

        is RunInspectorUiState.Loading -> InspectorLoading()

        is RunInspectorUiState.Ready -> ReadyInspection(state.inspection)

        is RunInspectorUiState.NotFound -> InspectorMessage(
            "This run is not available in the current process. Restored history is not inspectable yet.",
        )

        is RunInspectorUiState.Failed -> InspectorMessage(
            "The inspection could not be loaded safely. No provider request was started.",
        )
    }
}

@Composable
private fun InspectorLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Loading inspection…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InspectorMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyInspection(inspection: RunInspection) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { IdentitySection(inspection) }
        item { ParametersSection(inspection) }
        item { UsageSection(inspection.usage) }
        item { ResultSection(inspection.events.lastOrNull() as? RunInspectionEvent.Finished) }
        item { SectionTitle("Lifecycle timeline") }
        itemsIndexed(inspection.events) { index, event -> TimelineEvent(index + 1, event) }
    }
}
