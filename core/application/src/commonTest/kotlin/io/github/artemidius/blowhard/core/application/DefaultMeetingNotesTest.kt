package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultMeetingNotesTest {
    @Test
    fun `generated content remains distinct from optimistic user edits`() = runTest {
        val store = FakeMeetingNoteStore()
        val notes = service(store = store)

        val generated = assertIs<MeetingNoteGenerationOutcome.Generated>(notes.generate(SOURCE_ID)).note
        assertEquals("mistral-small-latest", generated.modelId)
        assertNull(generated.editedContent)
        assertEquals(0, generated.revision)

        val edited = generated.content.copy(title = "Edited title")
        val updated = assertIs<MeetingNoteUpdateOutcome.Updated>(notes.update(generated.id, 0, edited)).note
        assertEquals("Release sync", updated.generatedContent.title)
        assertEquals("Edited title", updated.content.title)
        assertEquals(1, updated.revision)
        assertIs<MeetingNoteUpdateOutcome.Conflict>(notes.update(generated.id, 0, edited))
    }

    @Test
    fun `JSON export round trips through strict validation and markdown keeps citations`() = runTest {
        val notes = service()
        val note = assertIs<MeetingNoteGenerationOutcome.Generated>(notes.generate(SOURCE_ID)).note

        val json = assertIs<MeetingNoteExportOutcome.Exported>(
            notes.export(note.id, MeetingNoteExportFormat.JSON),
        ).value
        val parsed = assertIs<MeetingNoteOutputValidation.Accepted>(
            MeetingNoteOutputParser().parse(json.content, EXTRACTION),
        ).content
        assertEquals(note.content, parsed)

        val markdown = assertIs<MeetingNoteExportOutcome.Exported>(
            notes.export(note.id, MeetingNoteExportFormat.MARKDOWN),
        ).value
        assertTrue(markdown.content.contains("## Decisions"))
        assertTrue(markdown.content.contains("Source page 1: ship on Friday"))
    }

    @Test
    fun `source and provider failures remain typed and publish nothing`() = runTest {
        val sources = listOf(
            DocumentExtractionStoreLookupResult.SourceNotFound to MeetingNoteGenerationRejection.DOCUMENT_NOT_FOUND,
            DocumentExtractionStoreLookupResult.NotProcessed to MeetingNoteGenerationRejection.DOCUMENT_NOT_PROCESSED,
            DocumentExtractionStoreLookupResult.Unavailable to MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE,
            DocumentExtractionStoreLookupResult.Found(
                EXTRACTION.copy(pages = listOf(ExtractedDocumentPage(0, "  "))),
            ) to MeetingNoteGenerationRejection.EMPTY_SOURCE,
        )
        sources.forEach { (source, expected) ->
            val store = FakeMeetingNoteStore()
            val result = service(source = source, store = store).generate(SOURCE_ID)
            assertEquals(expected, assertIs<MeetingNoteGenerationOutcome.Rejected>(result).reason)
            assertTrue(store.records.isEmpty())
        }

        MeetingNoteGenerationFailureKind.entries.forEach { kind ->
            val store = FakeMeetingNoteStore()
            val result = service(
                generated = MeetingNoteGenerationResult.Failed(kind),
                store = store,
            ).generate(SOURCE_ID)
            assertIs<MeetingNoteGenerationOutcome.Rejected>(result)
            assertTrue(store.records.isEmpty())
        }
    }

    @Test
    fun `invalid structured output and unsafe infrastructure fail closed`() = runTest {
        val invalid = service(generated = MeetingNoteGenerationResult.Generated("{}", "model"))
        assertEquals(
            MeetingNoteGenerationRejection.INVALID_OUTPUT,
            assertIs<MeetingNoteGenerationOutcome.Rejected>(invalid.generate(SOURCE_ID)).reason,
        )

        val failedClock = service(clock = MeetingNoteClock { error("private clock detail") })
        assertEquals(
            MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE,
            assertIs<MeetingNoteGenerationOutcome.Rejected>(failedClock.generate(SOURCE_ID)).reason,
        )

        val failedGenerator = service(generator = MeetingNoteGenerationPort { error("private provider detail") })
        assertEquals(
            MeetingNoteGenerationRejection.PROVIDER_UNAVAILABLE,
            assertIs<MeetingNoteGenerationOutcome.Rejected>(failedGenerator.generate(SOURCE_ID)).reason,
        )
    }

    @Test
    fun `cancellation is never converted into a rejection`() = runTest {
        val notes = service(generator = MeetingNoteGenerationPort { throw CancellationException("stop") })

        assertFailsWith<CancellationException> { notes.generate(SOURCE_ID) }
    }

    @Test
    fun `missing lookups updates and exports are explicit`() = runTest {
        val notes = service()
        val missing = MeetingNoteId("missing")

        assertIs<MeetingNoteLookupOutcome.NotFound>(notes.find(missing))
        assertIs<MeetingNoteUpdateOutcome.NotFound>(notes.update(missing, 0, CONTENT))
        assertIs<MeetingNoteUpdateOutcome.Conflict>(notes.update(missing, -1, CONTENT))
        assertIs<MeetingNoteExportOutcome.NotFound>(notes.export(missing, MeetingNoteExportFormat.JSON))
    }

    private fun service(
        source: DocumentExtractionStoreLookupResult = DocumentExtractionStoreLookupResult.Found(EXTRACTION),
        generated: MeetingNoteGenerationResult = MeetingNoteGenerationResult.Generated(
            VALID_JSON,
            "mistral-small-latest",
        ),
        generator: MeetingNoteGenerationPort = MeetingNoteGenerationPort { generated },
        store: FakeMeetingNoteStore = FakeMeetingNoteStore(),
        clock: MeetingNoteClock = MeetingNoteClock { 100 },
    ): DefaultMeetingNotes = DefaultMeetingNotes(
        documentStore = ExtractionStore(source),
        generator = generator,
        noteStore = store,
        idFactory = MeetingNoteIdFactory { MeetingNoteId("note-1") },
        clock = clock,
    )
}

private class ExtractionStore(private val result: DocumentExtractionStoreLookupResult) : DocumentProcessingStore {
    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult = result
    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = DocumentSourceLookupResult.Unavailable
    override suspend fun saveExtraction(extraction: DocumentExtraction) = DocumentExtractionSaveResult.UNAVAILABLE
    override suspend fun close() = Unit
}

private class FakeMeetingNoteStore : MeetingNoteStore {
    val records = mutableMapOf<MeetingNoteId, io.github.artemidius.blowhard.core.api.MeetingNoteRecord>()

    override suspend fun insert(
        note: io.github.artemidius.blowhard.core.api.MeetingNoteRecord,
    ): MeetingNoteInsertResult = if (note.id in records) {
        MeetingNoteInsertResult.ID_CONFLICT
    } else {
        records[note.id] = note
        MeetingNoteInsertResult.INSERTED
    }

    override suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup = records[id]
        ?.let(MeetingNoteStoreLookup::Found)
        ?: MeetingNoteStoreLookup.NotFound

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult {
        val current = records[id]
        return when {
            current == null -> MeetingNoteStoreUpdateResult.NotFound

            current.revision != expectedRevision -> MeetingNoteStoreUpdateResult.Conflict

            else -> current.copy(
                editedContent = editedContent,
                updatedAtEpochMilliseconds = updatedAtEpochMilliseconds,
                revision = current.revision + 1,
            ).also { records[id] = it }.let(MeetingNoteStoreUpdateResult::Updated)
        }
    }
}

private val SOURCE_ID = DocumentId("source-1")
private val EXTRACTION = DocumentExtraction(
    sourceDocumentId = SOURCE_ID,
    processor = "fixture-ocr-v1",
    processedAtEpochMilliseconds = 1,
    pages = listOf(ExtractedDocumentPage(0, "Decision: ship on Friday.")),
)
private val CITATION = MeetingNoteSourceReference(0, "ship on Friday")
private val CONTENT = MeetingNoteContent(
    title = "Release sync",
    decisions = listOf(MeetingDecision("Ship on Friday", listOf(CITATION))),
    actionItems = emptyList<MeetingActionItem>(),
)
private val VALID_JSON = """
    {
      "template_version":"meeting-note-v1",
      "title":"Release sync",
      "decisions":[{"summary":"Ship on Friday","sources":[{"page_index":0,"excerpt":"ship on Friday"}]}],
      "action_items":[]
    }
""".trimIndent()
