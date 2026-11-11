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
