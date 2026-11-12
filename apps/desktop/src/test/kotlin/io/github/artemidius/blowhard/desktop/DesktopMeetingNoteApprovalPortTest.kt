package io.github.artemidius.blowhard.desktop

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
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalProposalResult
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopMeetingNoteApprovalPortTest {
    @Test
    fun `approval writes exactly the presented local copy once`() = runTest {
        val store = ApprovalStore()
        val port = port(store)
        val proposed = assertIs<MeetingNoteApprovalProposalResult.Proposed>(port.propose(note()))

        assertEquals("Local meeting notes / Approved copies", proposed.state.destination)
        assertEquals(0, store.writes)
        assertEquals(ProposedMeetingNoteStatus.COMMITTED, port.approve())
        assertEquals(1, store.writes)
        assertEquals("Edited decision", store.stored?.generatedContent?.decisions?.single()?.summary)
        assertEquals(null, store.stored?.editedContent)
        assertEquals(0, store.stored?.revision)
    }

    @Test
    fun `denial never invokes the durable writer`() = runTest {
        val store = ApprovalStore()
        val port = port(store)
        port.propose(note())

        assertEquals(ProposedMeetingNoteStatus.DENIED, port.deny())
        assertEquals(0, store.writes)
    }

    @Test
    fun `interruption reconciles a committed receipt without replay`() = runTest {
        val committed = ApprovalStore(cancelDuringWrite = true, commitBeforeCancellation = true)
        val committedPort = port(committed)
        committedPort.propose(note())
        assertEquals(ProposedMeetingNoteStatus.COMMITTED, committedPort.approve())
        assertEquals(1, committed.writes)

        val notApplied = ApprovalStore(cancelDuringWrite = true)
        val interruptedPort = port(notApplied)
        interruptedPort.propose(note())
        assertEquals(ProposedMeetingNoteStatus.INTERRUPTED, interruptedPort.approve())
        assertEquals(1, notApplied.writes)
    }

    @Test
    fun `missing proposal and missing store remain disabled`() = runTest {
        val port = port(ApprovalStore())
        assertEquals(ProposedMeetingNoteStatus.FAILED, port.approve())
        assertEquals(ProposedMeetingNoteStatus.FAILED, port.deny())

        val unavailable = DesktopMeetingNoteApprovalPort.create(null)
        assertEquals(MeetingNoteApprovalProposalResult.Unavailable, unavailable.propose(note()))
    }

    private fun port(store: MeetingNoteStore): io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalPort {
        var nextIdentity = 0
        return DesktopMeetingNoteApprovalPort.create(store, now = { 100 }) { "identity-${nextIdentity++}" }
    }
}

private class ApprovalStore(
    private val cancelDuringWrite: Boolean = false,
    private val commitBeforeCancellation: Boolean = false,
) : MeetingNoteStore {
    var writes: Int = 0
    var stored: MeetingNoteRecord? = null
    private var receipt: MeetingNoteId? = null

    override suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult = MeetingNoteInsertResult.UNAVAILABLE

    override suspend fun insertApproved(write: ApprovedMeetingNoteWrite): ApprovedMeetingNoteInsertResult {
        writes += 1
        if (commitBeforeCancellation || !cancelDuringWrite) {
            stored = write.note
            receipt = write.note.id
        }
        if (cancelDuringWrite) throw CancellationException("synthetic interruption")
        return ApprovedMeetingNoteInsertResult.Inserted
    }

    override suspend fun findApprovedOperation(
        operationId: ApprovedNoteWriteOperationId,
    ): ApprovedMeetingNoteReceiptLookup = receipt?.let(ApprovedMeetingNoteReceiptLookup::Committed)
        ?: ApprovedMeetingNoteReceiptLookup.NotFound

    override suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup = stored
        ?.takeIf { it.id == id }
        ?.let(MeetingNoteStoreLookup::Found)
        ?: MeetingNoteStoreLookup.NotFound

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult = MeetingNoteStoreUpdateResult.Unavailable

    override suspend fun close(): Unit = Unit
}

private fun note(): MeetingNoteRecord {
    val source = MeetingNoteSourceReference(0, "Ship")
    val generated = MeetingNoteContent("Release", listOf(MeetingDecision("Ship", listOf(source))), emptyList())
    val edited = MeetingNoteContent("Release", listOf(MeetingDecision("Edited decision", listOf(source))), emptyList())
    return MeetingNoteRecord(
        MeetingNoteId("generated"),
        DocumentId("document"),
        "meeting-note-v1",
        "model",
        generated,
        edited,
        1,
        2,
        1,
    )
}
