package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ApplicationRuntimeDependenciesTest {
    @Test
    fun `conversation-only graph preserves required ports and rejects optional capabilities`() = runTest {
        val settings = FailClosedConnectionSettings()
        val generation = TextGenerationPort { flowOf(GenerationEvent.Completed) }
        val store = RecordingStore()

        val dependencies = ApplicationRuntimeDependencies.conversationOnly(settings, generation, store)

        assertSame(settings, dependencies.connectionSettings)
        assertSame(generation, dependencies.generation)
        assertSame(store, dependencies.conversationStore)
        assertEquals(ClipboardWriteResult.UNAVAILABLE, dependencies.clipboardWriter.write("text"))
        assertEquals(DocumentListResult.StorageUnavailable, dependencies.documentLibrary.list())
        assertEquals(
            MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE),
            dependencies.meetingNotes.generate(io.github.artemidius.blowhard.core.api.DocumentId("document")),
        )
    }

    @Test
    fun `document capabilities replace only fail-closed document ports`() {
        val base = ApplicationRuntimeDependencies.conversationOnly(
            FailClosedConnectionSettings(),
            TextGenerationPort { flowOf(GenerationEvent.Completed) },
        )

        val configured = base.withDocuments(
            library = RecordingDocumentLibrary,
            ocr = RecordingDocumentOcr,
            picker = RecordingDocumentPicker,
            processingStore = RecordingDocumentProcessingStore,
        )

        assertSame(RecordingDocumentLibrary, configured.documentLibrary)
        assertSame(RecordingDocumentOcr, configured.documentOcr)
        assertSame(RecordingDocumentPicker, configured.documentPicker)
        assertSame(RecordingDocumentProcessingStore, configured.documentProcessingStore)
        assertSame(base.connectionSettings, configured.connectionSettings)
        assertSame(base.generation, configured.generation)
        assertSame(base.meetingNotes, configured.meetingNotes)
    }
}

private object RecordingDocumentLibrary : DocumentLibrary {
    override suspend fun import(source: DocumentImportSource): DocumentImportResult = error("not called")
    override suspend fun list(): DocumentListResult = error("not called")
    override suspend fun find(id: DocumentId): DocumentLookupResult = error("not called")
    override suspend fun delete(id: DocumentId): DocumentDeletionResult = error("not called")
}

private object RecordingDocumentOcr : DocumentOcr {
    override suspend fun process(id: DocumentId): DocumentProcessingResult = error("not called")
    override suspend fun find(id: DocumentId): DocumentExtractionLookupResult = error("not called")
}

private object RecordingDocumentPicker : DocumentPicker {
    override suspend fun select(): DocumentImportSource? = error("not called")
}

private object RecordingDocumentProcessingStore : DocumentProcessingStore {
    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = error("not called")
    override suspend fun saveExtraction(
        extraction: io.github.artemidius.blowhard.core.api.DocumentExtraction,
    ): DocumentExtractionSaveResult = error("not called")

    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult = error("not called")
    override suspend fun close(): Unit = Unit
}
