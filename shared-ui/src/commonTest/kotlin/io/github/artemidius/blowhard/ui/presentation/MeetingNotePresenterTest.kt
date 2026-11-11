package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteExport
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingNotePresenterTest {
    @Test
    fun `generation publishes an editable typed draft`() = runTest {
        val service = FakeMeetingNotes()
        val presenter = MeetingNotePresenter(service, StandardTestDispatcher(testScheduler))

        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        runCurrent()

        assertEquals("Weekly sync", presenter.state.value.draft?.title)
        assertEquals("Ship safely", presenter.state.value.draft?.decisions?.single()?.summary)
        assertFalse(presenter.state.value.isBusy)
        presenter.close()
    }

    @Test
    fun `valid edits preserve evidence and advance the durable revision`() = runTest {
        val service = FakeMeetingNotes()
        val presenter = MeetingNotePresenter(service, StandardTestDispatcher(testScheduler))
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        runCurrent()

        presenter.dispatch(MeetingNoteAction.DecisionChanged(0, "Ship after review"))
        presenter.dispatch(MeetingNoteAction.ActionOwnerChanged(0, "Artem"))
        presenter.dispatch(MeetingNoteAction.ActionDueDateChanged(0, "2026-12-01"))
        presenter.dispatch(MeetingNoteAction.Save)
        runCurrent()

        assertEquals("Ship after review", service.updatedContent?.decisions?.single()?.summary)
        assertEquals(
            service.initial.generatedContent.decisions.single().sources,
            service.updatedContent?.decisions?.single()?.sources,
        )
        assertEquals(1, presenter.state.value.note?.revision)
        assertEquals(MeetingNoteNotice.Saved, presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `invalid date remains an editable draft but is not persisted`() = runTest {
        val service = FakeMeetingNotes()
        val presenter = MeetingNotePresenter(service, StandardTestDispatcher(testScheduler))
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        runCurrent()

        presenter.dispatch(MeetingNoteAction.ActionDueDateChanged(0, "tomorrow"))
        presenter.dispatch(MeetingNoteAction.Save)
        runCurrent()

        assertNull(service.updatedContent)
        assertEquals("tomorrow", presenter.state.value.draft?.actionItems?.single()?.dueDate)
        assertEquals(MeetingNoteNotice.InvalidEdit, presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `typed rejection and bounded export are presented without provider detail`() = runTest {
        val service = FakeMeetingNotes()
        val presenter = MeetingNotePresenter(service, StandardTestDispatcher(testScheduler))
        service.generation = MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.RATE_LIMITED)
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        runCurrent()
        assertEquals(MeetingNoteNotice.GenerationRejected("RATE_LIMITED"), presenter.state.value.notice)

        service.generation = MeetingNoteGenerationOutcome.Generated(service.initial)
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        presenter.dispatch(MeetingNoteAction.Export(MeetingNoteExportFormat.MARKDOWN))
        runCurrent()
        assertEquals("note.md", presenter.state.value.export?.suggestedFileName)

        presenter.dispatch(MeetingNoteAction.ExportDismissed)
        presenter.dispatch(MeetingNoteAction.NoticeDismissed)
        runCurrent()
        assertNull(presenter.state.value.export)
        assertNull(presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `approved proposal reaches a committed terminal state`() = runTest {
        val approvals = FakeMeetingNoteApprovalPort(ProposedMeetingNoteStatus.COMMITTED)
        val presenter = approvalPresenter(approvals)
        presenter.dispatch(MeetingNoteAction.ProposeWrite)
        runCurrent()
        assertEquals(ProposedMeetingNoteStatus.PENDING, presenter.state.value.approval?.status)

        presenter.dispatch(MeetingNoteAction.ApproveWrite)
        runCurrent()
        assertEquals(ProposedMeetingNoteStatus.COMMITTED, presenter.state.value.approval?.status)
        assertEquals(1, approvals.approvals)
        presenter.close()
    }

    @Test
    fun `denial and interruption remain explicit without a committed state`() = runTest {
        val denied = FakeMeetingNoteApprovalPort(ProposedMeetingNoteStatus.COMMITTED)
        val deniedPresenter = approvalPresenter(denied)
        deniedPresenter.dispatch(MeetingNoteAction.ProposeWrite)
        deniedPresenter.dispatch(MeetingNoteAction.DenyWrite)
        runCurrent()
        assertEquals(ProposedMeetingNoteStatus.DENIED, deniedPresenter.state.value.approval?.status)
        assertEquals(0, denied.approvals)
        deniedPresenter.close()

        val interrupted = FakeMeetingNoteApprovalPort(ProposedMeetingNoteStatus.INTERRUPTED)
        val interruptedPresenter = approvalPresenter(interrupted)
        interruptedPresenter.dispatch(MeetingNoteAction.ProposeWrite)
        interruptedPresenter.dispatch(MeetingNoteAction.ApproveWrite)
        runCurrent()
        assertEquals(ProposedMeetingNoteStatus.INTERRUPTED, interruptedPresenter.state.value.approval?.status)
        interruptedPresenter.close()
    }

    @Test
    fun `unavailable proposal fails safely and terminal approval can be dismissed`() = runTest {
        val presenter = MeetingNotePresenter(FakeMeetingNotes(), StandardTestDispatcher(testScheduler))
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        presenter.dispatch(MeetingNoteAction.ProposeWrite)
        runCurrent()
        assertEquals(MeetingNoteNotice.OperationUnavailable, presenter.state.value.notice)

        val approvals = FakeMeetingNoteApprovalPort(ProposedMeetingNoteStatus.COMMITTED)
        val available = approvalPresenter(approvals)
        available.dispatch(MeetingNoteAction.ProposeWrite)
        available.dispatch(MeetingNoteAction.ApproveWrite)
        available.dispatch(MeetingNoteAction.ApproveWrite)
        runCurrent()
        assertEquals(1, approvals.approvals)
        available.dispatch(MeetingNoteAction.ApprovalDismissed)
        runCurrent()
        assertNull(available.state.value.approval)
        presenter.close()
        available.close()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.approvalPresenter(
        approvals: MeetingNoteApprovalPort,
    ): MeetingNotePresenter {
        val presenter = MeetingNotePresenter(FakeMeetingNotes(), StandardTestDispatcher(testScheduler), approvals)
        presenter.dispatch(MeetingNoteAction.Generate(DocumentId("source")))
        runCurrent()
        return presenter
    }
}

private class FakeMeetingNoteApprovalPort(private val approvalResult: ProposedMeetingNoteStatus) :
    MeetingNoteApprovalPort {
    var approvals: Int = 0

    override suspend fun propose(note: MeetingNoteRecord): MeetingNoteApprovalProposalResult =
        MeetingNoteApprovalProposalResult.Proposed(
            ProposedMeetingNoteUiState(note, "Local meeting notes / Approved copy"),
        )

    override suspend fun approve(): ProposedMeetingNoteStatus {
        approvals += 1
        return approvalResult
    }

    override suspend fun deny(): ProposedMeetingNoteStatus = ProposedMeetingNoteStatus.DENIED
}

private class FakeMeetingNotes : MeetingNotes {
    val initial = note()
    var generation: MeetingNoteGenerationOutcome = MeetingNoteGenerationOutcome.Generated(initial)
    var updatedContent: MeetingNoteContent? = null

    override suspend fun generate(sourceDocumentId: DocumentId): MeetingNoteGenerationOutcome = generation

    override suspend fun find(id: MeetingNoteId): MeetingNoteLookupOutcome = MeetingNoteLookupOutcome.Found(initial)

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        content: MeetingNoteContent,
    ): MeetingNoteUpdateOutcome {
        updatedContent = content
        return MeetingNoteUpdateOutcome.Updated(
            initial.copy(editedContent = content, revision = 1, updatedAtEpochMilliseconds = 2),
        )
    }

    override suspend fun export(id: MeetingNoteId, format: MeetingNoteExportFormat): MeetingNoteExportOutcome =
        MeetingNoteExportOutcome.Exported(MeetingNoteExport("note.md", "text/markdown", "# Note"))
}

private fun note(): MeetingNoteRecord {
    val source = MeetingNoteSourceReference(0, "Ship safely")
    val content = MeetingNoteContent(
        title = "Weekly sync",
        decisions = listOf(MeetingDecision("Ship safely", listOf(source))),
        actionItems = listOf(MeetingActionItem("Review release", null, null, listOf(source))),
    )
    return MeetingNoteRecord(
        id = MeetingNoteId("note"),
        sourceDocumentId = DocumentId("source"),
        templateVersion = "meeting-note-v1",
        modelId = "model",
        generatedContent = content,
        editedContent = null,
        createdAtEpochMilliseconds = 1,
        updatedAtEpochMilliseconds = 1,
        revision = 0,
    )
}
