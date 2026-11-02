package io.github.artemidius.blowhard.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artemidius.blowhard.adapters.persistence.AndroidLocalDatabaseFiles
import io.github.artemidius.blowhard.composition.LocalMeetingNoteApprovalPort
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.domain.DocumentOcrPage
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalProposalResult
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Device evidence for bounded import into Android's real app-private SQLite store. */
@RunWith(AndroidJUnit4::class)
public class AndroidDocumentSessionDeviceTest {
    /** Proves inspected bytes remain durable after the document session is reopened. */
    @Test
    public fun importedDocumentSurvivesSessionRestart(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "documents-${UUID.randomUUID()}.db"
        val source = ByteDocumentSource("scan.png", PNG)
        val processor = RecordingDocumentOcrPort()
        val noteGeneration = MeetingNoteGenerationPort {
            MeetingNoteGenerationResult.Generated(MEETING_NOTE_JSON, "mobile-test-model")
        }

        try {
            val first = requireNotNull(
                AndroidDocumentSession.openOrNull(context, databaseName, processor, noteGeneration),
            )
            val imported = first.library.import(source) as DocumentImportResult.Imported
            assertTrue(first.ocr.process(imported.document.id) is DocumentProcessingResult.Processed)
            assertEquals(1, processor.requests)
            val generated = first.meetingNotes.generate(imported.document.id) as MeetingNoteGenerationOutcome.Generated
            val approvedNoteId = approveCopy(first, generated.note)
            first.close()

            val second = requireNotNull(
                AndroidDocumentSession.openOrNull(context, databaseName, processor, noteGeneration),
            )
            val stored = second.library.list() as DocumentListResult.Available
            assertEquals(listOf("scan.png"), stored.documents.map { it.displayName })
            assertTrue(second.meetingNotes.find(generated.note.id) is MeetingNoteLookupOutcome.Found)
            assertTrue(second.meetingNotes.find(approvedNoteId) is MeetingNoteLookupOutcome.Found)
            second.close()
        } finally {
            AndroidLocalDatabaseFiles.delete(context, databaseName)
        }
    }
}

private suspend fun approveCopy(session: AndroidDocumentSession, note: MeetingNoteRecord): MeetingNoteId {
    var nextIdentity = 0
    val approvals = LocalMeetingNoteApprovalPort.create(
        session.meetingNoteStore,
        now = { 100 },
        identity = {
            val value = "android-approval-$nextIdentity"
            nextIdentity += 1
            value
        },
    )
    val proposed = approvals.propose(note) as MeetingNoteApprovalProposalResult.Proposed
    assertEquals(ProposedMeetingNoteStatus.COMMITTED, approvals.approve())
    return proposed.state.note.id
}

private class RecordingDocumentOcrPort : DocumentOcrPort {
    var requests: Int = 0
        private set

    override suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult {
        requests += 1
        return DocumentOcrPortResult.Processed("mobile-test-ocr", listOf(DocumentOcrPage(0, "# Scan")))
    }
}

private class ByteDocumentSource(override val displayName: String, private val content: ByteArray) :
    DocumentImportSource {
    override val declaredSizeBytes: Long = content.size.toLong()

    override suspend fun read(maximumBytes: Long): DocumentReadResult = if (content.size.toLong() <= maximumBytes) {
        DocumentReadResult.Content(content)
    } else {
        DocumentReadResult.TooLarge
    }
}

private val PNG = hex(
    "89504e470d0a1a0a0000000d4948445200000002000000030806000000b9eade81" +
        "0000000b49444154789c636080020000090001fb52b8a90000000049454e44ae426082",
)

private val MEETING_NOTE_JSON = """
    {
      "template_version":"meeting-note-v1",
      "title":"Scan review",
      "decisions":[{"summary":"Keep the scan","sources":[{"page_index":0,"excerpt":"Scan"}]}],
      "action_items":[]
    }
""".trimIndent()

private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
    val offset = index * 2
    value.substring(offset, offset + 2).toInt(radix = 16).toByte()
}
