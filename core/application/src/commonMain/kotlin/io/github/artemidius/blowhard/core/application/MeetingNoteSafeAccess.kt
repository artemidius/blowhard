package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import kotlinx.coroutines.CancellationException

internal class MeetingNoteSafeAccess(
    private val documents: DocumentProcessingStore,
    private val generator: MeetingNoteGenerationPort,
    private val notes: MeetingNoteStore,
) {
    suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult = safely(
        DocumentExtractionStoreLookupResult.Unavailable,
    ) { documents.findExtraction(id) }

    suspend fun generate(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult = safely(
        MeetingNoteGenerationResult.Failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE),
    ) { generator.generate(request) }

    suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult =
        safely(MeetingNoteInsertResult.UNAVAILABLE) { notes.insert(note) }

    suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup =
        safely(MeetingNoteStoreLookup.Unavailable) { notes.find(id) }

    suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        content: MeetingNoteContent,
        updatedAt: Long,
    ): MeetingNoteStoreUpdateResult = safely(MeetingNoteStoreUpdateResult.Unavailable) {
        notes.update(id, expectedRevision, content, updatedAt)
    }

    private suspend fun <T> safely(fallback: T, operation: suspend () -> T): T = try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
}
