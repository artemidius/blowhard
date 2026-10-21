package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult

internal class EvaluationDocumentStore(private val extraction: DocumentExtraction) : DocumentProcessingStore {
    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = DocumentSourceLookupResult.NotFound

    override suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult =
        DocumentExtractionSaveResult.UNAVAILABLE

    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult =
        if (id == extraction.sourceDocumentId) {
            DocumentExtractionStoreLookupResult.Found(extraction)
        } else {
            DocumentExtractionStoreLookupResult.SourceNotFound
        }

    override suspend fun close() = Unit
}

internal class EvaluationNoteStore : MeetingNoteStore {
    private var note: MeetingNoteRecord? = null

    override suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult {
        if (this.note != null) return MeetingNoteInsertResult.ID_CONFLICT
        this.note = note
        return MeetingNoteInsertResult.INSERTED
    }

    override suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup = note
        ?.takeIf { it.id == id }
        ?.let(MeetingNoteStoreLookup::Found)
        ?: MeetingNoteStoreLookup.NotFound

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult = MeetingNoteStoreUpdateResult.Unavailable

    override suspend fun close() = Unit
}
