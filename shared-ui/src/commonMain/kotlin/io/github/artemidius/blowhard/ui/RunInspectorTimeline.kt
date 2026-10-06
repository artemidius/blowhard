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
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.RunInspectionEvent

@Composable
internal fun TimelineEvent(position: Int, event: RunInspectionEvent) {
    val (title, detail) = event.description()
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = INSPECTOR_WIDTH),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$position. $title", fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                "UTC epoch milliseconds: ${event.at.epochMilliseconds}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun RunInspectionEvent.description(): Pair<String, String> = when (this) {
    is RunInspectionEvent.Accepted -> "Accepted" to "The application accepted and identified the attempt."
    is RunInspectionEvent.Started -> "Provider started" to "The attempt obtained execution capacity."
    is RunInspectionEvent.Finished -> "Finished" to "The attempt reached ${status.displayStatus().lowercase()}."
}
