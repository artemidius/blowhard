package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial application service for validated generation, durable edits, and bounded exports. */
public class DefaultMeetingNotes(
    documentStore: io.github.artemidius.blowhard.core.domain.DocumentProcessingStore,
    generator: io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort,
    noteStore: io.github.artemidius.blowhard.core.domain.MeetingNoteStore,
    private val idFactory: MeetingNoteIdFactory,
    private val clock: MeetingNoteClock,
) : MeetingNotes {
    private val mutations = Mutex()
    private val parser = MeetingNoteOutputParser()
    private val access = MeetingNoteSafeAccess(documentStore, generator, noteStore)

    override suspend fun generate(sourceDocumentId: DocumentId): MeetingNoteGenerationOutcome = mutations.withLock {
        generateSerially(sourceDocumentId)
    }

    override suspend fun find(id: MeetingNoteId): MeetingNoteLookupOutcome = when (val found = access.find(id)) {
        is MeetingNoteStoreLookup.Found -> MeetingNoteLookupOutcome.Found(found.note)
        MeetingNoteStoreLookup.NotFound -> MeetingNoteLookupOutcome.NotFound
        MeetingNoteStoreLookup.Unavailable -> MeetingNoteLookupOutcome.StorageUnavailable
    }

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        content: MeetingNoteContent,
    ): MeetingNoteUpdateOutcome = mutations.withLock {
        val updatedAt = timeSafely()
        when {
            expectedRevision < 0 -> MeetingNoteUpdateOutcome.Conflict
            updatedAt == null -> MeetingNoteUpdateOutcome.StorageUnavailable
            else -> access.update(id, expectedRevision, content, updatedAt).toOutcome()
        }
    }

    override suspend fun export(id: MeetingNoteId, format: MeetingNoteExportFormat): MeetingNoteExportOutcome =
        when (val found = find(id)) {
            is MeetingNoteLookupOutcome.Found -> MeetingNoteExporter.export(found.note, format)
                ?.let(MeetingNoteExportOutcome::Exported)
                ?: MeetingNoteExportOutcome.ContentTooLarge

            MeetingNoteLookupOutcome.NotFound -> MeetingNoteExportOutcome.NotFound

            MeetingNoteLookupOutcome.StorageUnavailable -> MeetingNoteExportOutcome.StorageUnavailable
        }

    private suspend fun generateSerially(sourceDocumentId: DocumentId): MeetingNoteGenerationOutcome =
        when (val found = access.findExtraction(sourceDocumentId)) {
            is DocumentExtractionStoreLookupResult.Found -> generateFrom(found.extraction)

            DocumentExtractionStoreLookupResult.NotProcessed ->
                rejected(MeetingNoteGenerationRejection.DOCUMENT_NOT_PROCESSED)

            DocumentExtractionStoreLookupResult.SourceNotFound ->
                rejected(MeetingNoteGenerationRejection.DOCUMENT_NOT_FOUND)

            DocumentExtractionStoreLookupResult.Unavailable ->
                rejected(MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE)
        }

    private suspend fun generateFrom(extraction: DocumentExtraction): MeetingNoteGenerationOutcome {
        val request = try {
            MeetingNoteTemplateV1.request(extraction)
        } catch (_: IllegalArgumentException) {
            null
        }
        return when {
            extraction.pages.all { it.markdown.isBlank() } -> rejected(MeetingNoteGenerationRejection.EMPTY_SOURCE)

            request == null -> rejected(MeetingNoteGenerationRejection.INPUT_TOO_LARGE)

            else -> when (val generated = access.generate(request)) {
                is MeetingNoteGenerationResult.Generated -> publish(extraction, generated)
                is MeetingNoteGenerationResult.Failed -> rejected(generated.kind.toRejection())
            }
        }
    }

    private suspend fun publish(
        extraction: DocumentExtraction,
        generated: MeetingNoteGenerationResult.Generated,
    ): MeetingNoteGenerationOutcome {
        val parsed = parser.parse(generated.json, extraction)
        val content = (parsed as? MeetingNoteOutputValidation.Accepted)?.content
        return if (content == null) {
            rejected(MeetingNoteGenerationRejection.INVALID_OUTPUT)
        } else {
            publishValidated(extraction.sourceDocumentId, generated.modelId, content)
        }
    }

    private suspend fun publishValidated(
        sourceDocumentId: DocumentId,
        modelId: String,
        content: MeetingNoteContent,
    ): MeetingNoteGenerationOutcome {
        val record = MeetingNoteRecordFactory.create(sourceDocumentId, modelId, content, idFactory, timeSafely())
            ?: return storageRejected()
        return when (access.insert(record)) {
            MeetingNoteInsertResult.INSERTED -> MeetingNoteGenerationOutcome.Generated(record)
            MeetingNoteInsertResult.SOURCE_NOT_FOUND -> rejected(MeetingNoteGenerationRejection.DOCUMENT_NOT_FOUND)
            MeetingNoteInsertResult.ID_CONFLICT, MeetingNoteInsertResult.UNAVAILABLE -> storageRejected()
        }
    }

    private fun timeSafely(): Long? = try {
        clock.nowEpochMilliseconds().takeIf { it >= 0 }
    } catch (_: Exception) {
        null
    }
}

private fun MeetingNoteStoreUpdateResult.toOutcome(): MeetingNoteUpdateOutcome = when (this) {
    is MeetingNoteStoreUpdateResult.Updated -> MeetingNoteUpdateOutcome.Updated(note)
    MeetingNoteStoreUpdateResult.NotFound -> MeetingNoteUpdateOutcome.NotFound
    MeetingNoteStoreUpdateResult.Conflict -> MeetingNoteUpdateOutcome.Conflict
    MeetingNoteStoreUpdateResult.Unavailable -> MeetingNoteUpdateOutcome.StorageUnavailable
}

private fun MeetingNoteGenerationFailureKind.toRejection(): MeetingNoteGenerationRejection = when (this) {
    MeetingNoteGenerationFailureKind.AUTHENTICATION -> MeetingNoteGenerationRejection.AUTHENTICATION_REQUIRED
    MeetingNoteGenerationFailureKind.RATE_LIMITED -> MeetingNoteGenerationRejection.RATE_LIMITED
    MeetingNoteGenerationFailureKind.INPUT_REJECTED -> MeetingNoteGenerationRejection.INPUT_TOO_LARGE
    MeetingNoteGenerationFailureKind.REMOTE_FAILURE -> MeetingNoteGenerationRejection.PROVIDER_UNAVAILABLE
    MeetingNoteGenerationFailureKind.INVALID_RESPONSE -> MeetingNoteGenerationRejection.INVALID_OUTPUT
}

private fun rejected(reason: MeetingNoteGenerationRejection): MeetingNoteGenerationOutcome =
    MeetingNoteGenerationOutcome.Rejected(reason)

private fun storageRejected(): MeetingNoteGenerationOutcome =
    rejected(MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE)
