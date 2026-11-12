package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.db.SqlDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteReceiptLookup
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteWrite
import io.github.artemidius.blowhard.core.domain.ApprovedNoteWriteOperationId
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SqlDelightMeetingNoteStore(private val driver: SqlDriver) : MeetingNoteStore {
    private val database = BlowhardDatabase(driver)
    private val queries = database.meetingNoteQueries
    private val access = Mutex()
    private var closed = false

    override suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult =
        safely(MeetingNoteInsertResult.UNAVAILABLE) {
            var result = MeetingNoteInsertResult.UNAVAILABLE
            queries.transaction {
                result = when {
                    queries.meetingNoteExists(note.id.value).executeAsOne() -> MeetingNoteInsertResult.ID_CONFLICT

                    !database.documentExtractionQueries.sourceExists(note.sourceDocumentId.value).executeAsOne() ->
                        MeetingNoteInsertResult.SOURCE_NOT_FOUND

                    else -> {
                        insertNote(note)
                        MeetingNoteInsertResult.INSERTED
                    }
                }
            }
            result
        }

    override suspend fun insertApproved(write: ApprovedMeetingNoteWrite): ApprovedMeetingNoteInsertResult =
        safely(ApprovedMeetingNoteInsertResult.Unavailable) {
            var result: ApprovedMeetingNoteInsertResult = ApprovedMeetingNoteInsertResult.Unavailable
            queries.transaction {
                val committedReceipt = queries.findMeetingNoteWriteReceipt(write.operationId.value) {
                        approvalId,
                        runId,
                        callId,
                        argumentsJson,
                        noteId,
                    ->
                    ApprovedWriteReceipt(approvalId, runId, callId, argumentsJson, noteId)
                }.executeAsOneOrNull()
                result = when {
                    committedReceipt?.matches(write) == true -> ApprovedMeetingNoteInsertResult.AlreadyCommitted(
                        io.github.artemidius.blowhard.core.api.MeetingNoteId(committedReceipt.noteId),
                    )

                    committedReceipt != null -> ApprovedMeetingNoteInsertResult.OperationConflict

                    queries.meetingNoteExists(write.note.id.value).executeAsOne() ->
                        ApprovedMeetingNoteInsertResult.IdConflict

                    !database.documentExtractionQueries
                        .sourceExists(write.note.sourceDocumentId.value)
                        .executeAsOne() ->
                        ApprovedMeetingNoteInsertResult.SourceNotFound

                    else -> {
                        insertNote(write.note)
                        queries.insertMeetingNoteWriteReceipt(
                            operation_id = write.operationId.value,
                            approval_id = write.approvalId,
                            run_id = write.runId,
                            call_id = write.callId,
                            arguments_json = write.argumentsJson,
                            note_id = write.note.id.value,
                        )
                        ApprovedMeetingNoteInsertResult.Inserted
                    }
                }
            }
            result
        }

    override suspend fun findApprovedOperation(
        operationId: ApprovedNoteWriteOperationId,
    ): ApprovedMeetingNoteReceiptLookup = safely(ApprovedMeetingNoteReceiptLookup.Unavailable) {
        queries.findMeetingNoteWriteReceipt(operationId.value) { _, _, _, _, noteId -> noteId }
            .executeAsOneOrNull()
            ?.let { noteId ->
                ApprovedMeetingNoteReceiptLookup.Committed(
                    io.github.artemidius.blowhard.core.api.MeetingNoteId(noteId),
                )
            }
            ?: ApprovedMeetingNoteReceiptLookup.NotFound
    }

    override suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup =
        safely(MeetingNoteStoreLookup.Unavailable) { findInternal(id) }

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult = safely(MeetingNoteStoreUpdateResult.Unavailable) {
        var result: MeetingNoteStoreUpdateResult = MeetingNoteStoreUpdateResult.Unavailable
        queries.transaction {
            val changed = queries.updateMeetingNoteEdit(
                edited_json = MeetingNoteContentCodec.encode(editedContent),
                updated_at_epoch_ms = updatedAtEpochMilliseconds,
                id = id.value,
                revision = expectedRevision,
            ).value
            result = when {
                changed == 1L -> when (val found = findInternal(id)) {
                    is MeetingNoteStoreLookup.Found -> MeetingNoteStoreUpdateResult.Updated(found.note)
                    else -> MeetingNoteStoreUpdateResult.Unavailable
                }

                queries.meetingNoteExists(id.value).executeAsOne() -> MeetingNoteStoreUpdateResult.Conflict

                else -> MeetingNoteStoreUpdateResult.NotFound
            }
        }
        result
    }

    override suspend fun close(): Unit = access.withLock {
        if (closed) return
        closed = true
        driver.close()
    }

    private fun findInternal(id: MeetingNoteId): MeetingNoteStoreLookup = queries.findMeetingNote(id.value) {
            storedId,
            documentId,
            templateVersion,
            modelId,
            generatedJson,
            editedJson,
            createdAt,
            updatedAt,
            revision,
        ->
        MeetingNoteRecord(
            id = MeetingNoteId(storedId),
            sourceDocumentId = DocumentId(documentId),
            templateVersion = templateVersion,
            modelId = modelId,
            generatedContent = requireNotNull(MeetingNoteContentCodec.decode(generatedJson)),
            editedContent = editedJson?.let { requireNotNull(MeetingNoteContentCodec.decode(it)) },
            createdAtEpochMilliseconds = createdAt,
            updatedAtEpochMilliseconds = updatedAt,
            revision = revision,
        )
    }.executeAsOneOrNull()?.let(MeetingNoteStoreLookup::Found) ?: MeetingNoteStoreLookup.NotFound

    private fun insertNote(note: MeetingNoteRecord) {
        queries.insertMeetingNote(
            id = note.id.value,
            document_id = note.sourceDocumentId.value,
            template_version = note.templateVersion,
            model_id = note.modelId,
            generated_json = MeetingNoteContentCodec.encode(note.generatedContent),
            edited_json = note.editedContent?.let(MeetingNoteContentCodec::encode),
            created_at_epoch_ms = note.createdAtEpochMilliseconds,
            updated_at_epoch_ms = note.updatedAtEpochMilliseconds,
            revision = note.revision,
        )
    }

    private suspend fun <T> safely(fallback: T, operation: () -> T): T = try {
        access.withLock { if (closed) fallback else operation() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
}

private data class ApprovedWriteReceipt(
    val approvalId: String,
    val runId: String,
    val callId: String,
    val argumentsJson: String,
    val noteId: String,
) {
    fun matches(write: ApprovedMeetingNoteWrite): Boolean = approvalId == write.approvalId &&
        runId == write.runId &&
        callId == write.callId &&
        argumentsJson == write.argumentsJson &&
        noteId == write.note.id.value
}
