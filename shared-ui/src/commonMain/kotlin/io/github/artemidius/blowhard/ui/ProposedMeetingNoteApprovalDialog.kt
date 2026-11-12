@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteStatus
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteUiState

internal const val NOTE_APPROVAL_DIALOG_DESCRIPTION = "Review proposed local note write"
internal const val NOTE_APPROVAL_CONFIRM_DESCRIPTION = "Approve exact proposed note write"
internal const val NOTE_APPROVAL_DENY_DESCRIPTION = "Deny proposed note write"

/** Presents the exact content and local destination before enabling a mutation. */
@Composable
internal fun ProposedMeetingNoteApprovalDialog(
    state: ProposedMeetingNoteUiState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.semantics { contentDescription = NOTE_APPROVAL_DIALOG_DESCRIPTION },
        onDismissRequest = { if (state.canDeny) onDeny() else onDismiss() },
        title = { Text("Approve local note write?") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ApprovalDestination(state.destination)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NotePreview(state.note.content)
                Text(
                    state.status.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        confirmButton = {
            ApprovalConfirmButton(state, onApprove, onDismiss)
        },
        dismissButton = {
            TextButton(
                enabled = state.canDeny,
                onClick = onDeny,
                modifier = Modifier.semantics { contentDescription = NOTE_APPROVAL_DENY_DESCRIPTION },
            ) { Text("Deny") }
        },
    )
}

@Composable
private fun ApprovalConfirmButton(state: ProposedMeetingNoteUiState, onApprove: () -> Unit, onDismiss: () -> Unit) {
    if (state.status != ProposedMeetingNoteStatus.PENDING && state.status != ProposedMeetingNoteStatus.WRITING) {
        Button(onClick = onDismiss) { Text("Done") }
    } else {
        Button(
            enabled = state.canApprove,
            onClick = onApprove,
            modifier = Modifier.semantics { contentDescription = NOTE_APPROVAL_CONFIRM_DESCRIPTION },
        ) { Text(if (state.status == ProposedMeetingNoteStatus.WRITING) "Writing…" else "Approve write") }
    }
}

@Composable
private fun ApprovalDestination(destination: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Destination", style = MaterialTheme.typography.labelMedium)
        Text(destination, fontWeight = FontWeight.SemiBold)
        Text(
            "Only this exact note will be written. Editing it requires a new approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotePreview(content: MeetingNoteContent) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(content.title ?: "Untitled meeting note", style = MaterialTheme.typography.titleMedium)
        PreviewSection("Decisions", content.decisions, MeetingDecision::preview)
        PreviewSection("Action items", content.actionItems, MeetingActionItem::preview)
    }
}

@Composable
private fun <T> PreviewSection(title: String, values: List<T>, preview: (T) -> String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (values.isEmpty()) {
            Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            values.forEach { value -> Text("• ${preview(value)}", modifier = Modifier.padding(start = 4.dp)) }
        }
    }
}

private fun MeetingDecision.preview(): String = summary

private fun MeetingActionItem.preview(): String = buildString {
    append(description)
    owner?.let { append(" · ").append(it) }
    dueDate?.let { append(" · ").append(it.value) }
}

private fun ProposedMeetingNoteStatus.label(): String = when (this) {
    ProposedMeetingNoteStatus.PENDING -> "Waiting for your explicit approval"
    ProposedMeetingNoteStatus.WRITING -> "Writing to local application storage"
    ProposedMeetingNoteStatus.COMMITTED -> "Saved locally"
    ProposedMeetingNoteStatus.DENIED -> "Write denied; nothing was saved"
    ProposedMeetingNoteStatus.EXPIRED -> "Approval expired; review a new proposal"
    ProposedMeetingNoteStatus.INTERRUPTED -> "Write interrupted; recovery is required"
    ProposedMeetingNoteStatus.UNCERTAIN -> "Storage outcome is uncertain; the app will not repeat the write"
    ProposedMeetingNoteStatus.FAILED -> "Write failed; nothing was silently retried"
}
