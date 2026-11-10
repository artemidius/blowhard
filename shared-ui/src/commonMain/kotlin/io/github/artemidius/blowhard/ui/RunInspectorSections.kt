@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunTerminalStatus
import io.github.artemidius.blowhard.core.api.TokenUsage

@Composable
internal fun IdentitySection(inspection: RunInspection) {
    InspectorSection("Execution") {
        InspectorValue("Run", inspection.runId.value)
        InspectorValue("Conversation", inspection.conversationId.value)
        when (val target = inspection.target) {
            GenerationTarget.Demo -> {
                InspectorValue("Mode", "Offline demo")
                InspectorValue("Provider", "Deterministic fixture")
            }

            is GenerationTarget.Live -> {
                InspectorValue("Mode", "Live")
                InspectorValue("Provider", "Mistral")
                InspectorValue("Model", target.modelId)
            }
        }
    }
}

@Composable
internal fun ParametersSection(inspection: RunInspection) {
    InspectorSection("Generation controls") {
        InspectorValue("Maximum output tokens", inspection.parameters.maxOutputTokens?.toString() ?: "Provider default")
        InspectorValue("Temperature", inspection.parameters.temperature?.toString() ?: "Provider default")
    }
}

@Composable
internal fun UsageSection(usage: TokenUsage?) {
    InspectorSection("Provider-reported usage") {
        InspectorValue("Input tokens", usage?.inputTokens.displayCount())
        InspectorValue("Output tokens", usage?.outputTokens.displayCount())
        InspectorValue("Total tokens", usage?.totalTokens.displayCount())
        Text(
            "Unknown values are never converted to zero.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResultSection(finished: RunInspectionEvent.Finished?) {
    InspectorSection("Latest outcome") {
        InspectorValue("Status", finished?.status.displayStatus())
        finished?.failureKind?.let { InspectorValue("Failure category", it.name.readable()) }
        finished?.retryAdvice?.let { InspectorValue("Retry policy", it.name.readable()) }
        finished?.retryAfterSeconds?.let { InspectorValue("Retry after", "$it seconds") }
    }
}

@Composable
internal fun InspectorSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = INSPECTOR_WIDTH),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().widthIn(max = INSPECTOR_WIDTH),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun InspectorValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal fun RunTerminalStatus?.displayStatus(): String = when (this) {
    null -> "In progress"
    RunTerminalStatus.SUCCEEDED -> "Succeeded"
    RunTerminalStatus.FAILED -> "Failed"
    RunTerminalStatus.CANCELLED -> "Cancelled"
}

private fun Long?.displayCount(): String = this?.toString() ?: "Unknown"

private fun String.readable(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal val INSPECTOR_WIDTH = 880.dp
