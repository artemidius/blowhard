package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteReceiptLookup
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteWrite
import io.github.artemidius.blowhard.core.domain.ApprovedNoteWriteOperationId
import io.github.artemidius.blowhard.core.domain.MeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreUpdateResult
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApprovedMeetingNoteWriterTest {
    @Test
    fun `write is impossible until the exact note proposal is approved`() = runTest {
        val store = ApprovalMeetingNoteStore()
        val writer = writer(store)
        val prepared = writer.proposal(note())

        assertEquals(ToolCallOutcome.REJECTED, writer.write(prepared).outcome)
        assertEquals(0, store.inserts)
        assertEquals(ToolApprovalOutcome.ACCEPTED, writer.approve(prepared.binding.id))
        assertEquals(ToolCallOutcome.SUCCEEDED, writer.write(prepared).outcome)
        assertEquals(1, store.inserts)
        assertEquals(ToolCallOutcome.REJECTED, writer.write(prepared).outcome)
        assertEquals(1, store.inserts)
    }

    @Test
    fun `denial and changed proposal values never reach storage`() = runTest {
        val store = ApprovalMeetingNoteStore()
        val writer = writer(store)
        val prepared = writer.proposal(note())
        writer.deny(prepared.binding.id)

        assertEquals(ToolCallOutcome.REJECTED, writer.write(prepared).outcome)
        val changed = prepared.copy(
            binding = prepared.binding.copy(destination = ToolApprovalDestination("Different notebook")),
        )
        assertEquals(ToolCallOutcome.REJECTED, writer.write(changed).outcome)
        assertEquals(0, store.inserts)
    }

    @Test
    fun `identical deterministic conflict is idempotent but foreign collision is rejected`() = runTest {
        val record = note()
        val idempotentStore = ApprovalMeetingNoteStore(
            ApprovedMeetingNoteInsertResult.AlreadyCommitted(record.id),
            record,
        )
        val idempotentWriter = writer(idempotentStore)
        val idempotent = idempotentWriter.proposal(record)
        idempotentWriter.approve(idempotent.binding.id)
        assertEquals(ToolCallOutcome.SUCCEEDED, idempotentWriter.write(idempotent).outcome)

        val collisionStore = ApprovalMeetingNoteStore(ApprovedMeetingNoteInsertResult.IdConflict)
        val collisionWriter = writer(collisionStore)
        val collision = collisionWriter.proposal(record)
        collisionWriter.approve(collision.binding.id)
        assertEquals(ToolCallOutcome.REJECTED, collisionWriter.write(collision).outcome)
    }

    @Test
    fun `recovery proves committed not-applied and uncertain outcomes without replay`() = runTest {
        val record = note()
        val committedStore = ApprovalMeetingNoteStore(stored = record, receiptNoteId = record.id)
        val writer = writer(committedStore)
        val operationId = ApprovedNoteWriteOperationId("operation")
        assertIs<ApprovedMeetingNoteRecovery.Committed>(writer.recover(operationId))
        committedStore.receiptNoteId = null
        assertEquals(ApprovedMeetingNoteRecovery.NotApplied, writer.recover(operationId))
        committedStore.receiptUnavailable = true
        assertEquals(ApprovedMeetingNoteRecovery.Uncertain, writer.recover(operationId))
        assertEquals(0, committedStore.inserts)
    }

    @Test
    fun `unavailable write reports uncertainty and oversized proposal is refused`() = runTest {
        val store = ApprovalMeetingNoteStore(ApprovedMeetingNoteInsertResult.Unavailable)
        val writer = writer(store)
        val prepared = writer.proposal(note())
        writer.approve(prepared.binding.id)
        val result = writer.write(prepared)
        assertEquals(ToolCallOutcome.FAILED, result.outcome)
        assertTrue(result.outputJson.contains("write_outcome_uncertain"))

        val huge = note(
            content = MeetingNoteContent(
                null,
                (0 until 20).map { index ->
                    MeetingDecision("d".repeat(2_000), listOf(MeetingNoteSourceReference(index, "e".repeat(500))))
                },
                emptyList(),
            ),
        )
        assertEquals(
            ApprovedMeetingNotePreparation.ContentTooLarge,
            writer.prepare(
                huge,
                ToolApprovalId("large"),
                ToolApprovalRunId("large-run"),
                ToolCallId("large-call"),
                ToolApprovalDestination("Local notes"),
                1_000,
            ),
        )
    }

    private fun writer(store: MeetingNoteStore): ApprovedMeetingNoteWriter =
        ApprovedMeetingNoteWriter.create(store, ToolApprovalClock { 0 })
}

private suspend fun ApprovedMeetingNoteWriter.proposal(note: MeetingNoteRecord): ApprovedMeetingNoteProposal =
    assertIs<ApprovedMeetingNotePreparation.Proposed>(
        prepare(
            note,
            ToolApprovalId("approval-${note.modelId}"),
            ToolApprovalRunId("run-${note.modelId}"),
            ToolCallId("call-${note.modelId}"),
            ToolApprovalDestination("Local meeting notes / ${note.id.value}"),
            1_000,
        ),
    ).value

private class ApprovalMeetingNoteStore(
    var approvedInsertResult: ApprovedMeetingNoteInsertResult = ApprovedMeetingNoteInsertResult.Inserted,
    var stored: MeetingNoteRecord? = null,
    var receiptNoteId: MeetingNoteId? = null,
) : MeetingNoteStore {
    var inserts: Int = 0
    var lookupUnavailable: Boolean = false
    var receiptUnavailable: Boolean = false

    override suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult {
        error("Unapproved insert must not be used")
    }

    override suspend fun insertApproved(write: ApprovedMeetingNoteWrite): ApprovedMeetingNoteInsertResult {
        inserts += 1
        if (approvedInsertResult == ApprovedMeetingNoteInsertResult.Inserted) {
            stored = write.note
            receiptNoteId = write.note.id
        }
        return approvedInsertResult
    }

    override suspend fun findApprovedOperation(
        operationId: ApprovedNoteWriteOperationId,
    ): ApprovedMeetingNoteReceiptLookup = when {
        receiptUnavailable -> ApprovedMeetingNoteReceiptLookup.Unavailable
        receiptNoteId != null -> ApprovedMeetingNoteReceiptLookup.Committed(requireNotNull(receiptNoteId))
        else -> ApprovedMeetingNoteReceiptLookup.NotFound
    }

    override suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup = when {
        lookupUnavailable -> MeetingNoteStoreLookup.Unavailable
        stored?.id == id -> MeetingNoteStoreLookup.Found(requireNotNull(stored))
        else -> MeetingNoteStoreLookup.NotFound
    }

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult = MeetingNoteStoreUpdateResult.Unavailable

    override suspend fun close(): Unit = Unit
}

private fun note(
    model: String = "model",
    content: MeetingNoteContent = MeetingNoteContent(
        "Release",
        listOf(MeetingDecision("Ship Friday", listOf(MeetingNoteSourceReference(0, "Ship on Friday")))),
        emptyList(),
    ),
): MeetingNoteRecord = MeetingNoteRecord(
    MeetingNoteId("note-1"),
    DocumentId("document-1"),
    "meeting-note-v1",
    model,
    content,
    null,
    100,
    100,
    0,
)
