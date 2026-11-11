package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class DesktopMeetingNoteStoreTest {
    @Test
    internal fun generatedAndEditedValuesSurviveReopenSeparately() = withTemporaryDatabase { path ->
        saveDocument(path)
        DesktopMeetingNoteStores.open(path).useMeetingNoteStore { store ->
            assertEquals(MeetingNoteInsertResult.INSERTED, store.insert(record()))
            val edited = content().copy(title = "Edited release sync")
            val updated = assertIs<MeetingNoteStoreUpdateResult.Updated>(
                store.update(NOTE_ID, 0, edited, UPDATED_AT),
            ).note
            assertEquals("Release sync", updated.generatedContent.title)
            assertEquals("Edited release sync", updated.content.title)
            assertEquals(1, updated.revision)
            assertIs<MeetingNoteStoreUpdateResult.Conflict>(store.update(NOTE_ID, 0, edited, STALE_UPDATE_AT))
        }

        DesktopMeetingNoteStores.open(path).useMeetingNoteStore { store ->
            val found = assertIs<MeetingNoteStoreLookup.Found>(store.find(NOTE_ID)).note
            assertEquals("Release sync", found.generatedContent.title)
            assertEquals("Edited release sync", found.editedContent?.title)
            assertEquals(1, found.revision)
        }
    }

    @Test
    internal fun missingSourceDuplicateIdentityAndClosedStoreAreDistinct() = withTemporaryDatabase { path ->
        val store = DesktopMeetingNoteStores.open(path)
        assertEquals(MeetingNoteInsertResult.SOURCE_NOT_FOUND, store.insert(record()))
        saveDocument(path)
        assertEquals(MeetingNoteInsertResult.INSERTED, store.insert(record()))
        assertEquals(MeetingNoteInsertResult.ID_CONFLICT, store.insert(record()))

        store.close()
        store.close()
        assertEquals(MeetingNoteInsertResult.UNAVAILABLE, store.insert(record()))
        assertEquals(MeetingNoteStoreLookup.Unavailable, store.find(NOTE_ID))
        assertEquals(
            MeetingNoteStoreUpdateResult.Unavailable,
            store.update(NOTE_ID, 0, content(), CLOSED_UPDATE_AT),
        )
    }

    @Test
    internal fun sourceDeletionCascadesAndCorruptRowsFailClosed() = withTemporaryDatabase { path ->
        saveDocument(path)
        DesktopMeetingNoteStores.open(path).useMeetingNoteStore { store ->
            assertEquals(MeetingNoteInsertResult.INSERTED, store.insert(record()))
        }
        corruptGeneratedContent(path)
        DesktopMeetingNoteStores.open(path).useMeetingNoteStore { store ->
            assertEquals(MeetingNoteStoreLookup.Unavailable, store.find(NOTE_ID))
        }

        DesktopDocumentStores.open(path).let { documents ->
            try {
                documents.delete(DOCUMENT_ID)
            } finally {
                documents.close()
            }
        }
        assertEquals(0L, noteRowCount(path))
    }

    private suspend fun saveDocument(path: String) {
        val document = ImportedDocument(
            id = DOCUMENT_ID,
            displayName = "meeting.pdf",
            sizeBytes = SOURCE_BYTES.size.toLong(),
            importedAtEpochMilliseconds = 1,
            content = DocumentContentDescriptor.Pdf(1),
        )
        DesktopDocumentStores.open(path).let { store ->
            try {
                assertEquals(DocumentSaveResult.SAVED, store.save(document, SOURCE_BYTES))
            } finally {
                store.close()
            }
        }
    }

    private fun record(): MeetingNoteRecord = MeetingNoteRecord(
        id = NOTE_ID,
        sourceDocumentId = DOCUMENT_ID,
        templateVersion = "meeting-note-v1",
        modelId = "mistral-small-latest",
        generatedContent = content(),
        editedContent = null,
        createdAtEpochMilliseconds = 10,
        updatedAtEpochMilliseconds = 10,
        revision = 0,
    )

    private fun content(): MeetingNoteContent {
        val source = MeetingNoteSourceReference(0, "ship on Friday")
        return MeetingNoteContent(
            title = "Release sync",
            decisions = listOf(MeetingDecision("Ship on Friday", listOf(source))),
            actionItems = listOf(
                MeetingActionItem("Prepare release", null, MeetingNoteDate("2026-12-04"), listOf(source)),
            ),
        )
    }

    private fun corruptGeneratedContent(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE meeting_note SET generated_json = '{}' WHERE id = 'note-1'")
            }
        }
    }

    private fun noteRowCount(path: String): Long = DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM meeting_note").use { rows ->
                check(rows.next())
                rows.getLong(1)
            }
        }
    }

    private companion object {
        val DOCUMENT_ID = DocumentId("document-1")
        val NOTE_ID = MeetingNoteId("note-1")
        val SOURCE_BYTES = "source".encodeToByteArray()
        const val UPDATED_AT = 20L
        const val STALE_UPDATE_AT = 21L
        const val CLOSED_UPDATE_AT = 30L
    }
}

private suspend fun <T> MeetingNoteStore.useMeetingNoteStore(block: suspend (MeetingNoteStore) -> T): T = try {
    block(this)
} finally {
    close()
}
