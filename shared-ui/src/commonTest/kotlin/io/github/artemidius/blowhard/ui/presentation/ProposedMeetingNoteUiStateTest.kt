package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProposedMeetingNoteUiStateTest {
    @Test
    fun `only a pending exact proposal exposes consent actions`() {
        val pending = ProposedMeetingNoteUiState(note(), "Local meeting notes / release")
        assertTrue(pending.canApprove)
        assertTrue(pending.canDeny)

        ProposedMeetingNoteStatus.entries.filterNot { it == ProposedMeetingNoteStatus.PENDING }.forEach { status ->
            val terminal = pending.copy(status = status)
            assertFalse(terminal.canApprove)
            assertFalse(terminal.canDeny)
        }
    }
}

private fun note(): MeetingNoteRecord = MeetingNoteRecord(
    MeetingNoteId("note"),
    DocumentId("document"),
    "meeting-note-v1",
    "model",
    MeetingNoteContent(
        "Release",
        listOf(MeetingDecision("Ship", listOf(MeetingNoteSourceReference(0, "Ship")))),
        emptyList(),
    ),
    null,
    1,
    1,
    0,
)
