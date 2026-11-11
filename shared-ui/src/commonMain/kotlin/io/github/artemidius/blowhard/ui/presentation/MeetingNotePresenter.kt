package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Serializes explicit generation, editing, and export into immutable UI state. */
public class MeetingNotePresenter(
    private val notes: MeetingNotes,
    coroutineContext: CoroutineContext,
    private val approvals: MeetingNoteApprovalPort = UnavailableMeetingNoteApprovalPort,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val actions = Channel<MeetingNoteAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(MeetingNoteUiState())

    /** Current note editor state. */
    public val state: StateFlow<MeetingNoteUiState> = mutableState.asStateFlow()

    init {
        scope.launch { processActions() }
    }

    internal fun dispatch(action: MeetingNoteAction) {
        actions.trySend(action)
    }

    /** Stops presenter-owned work without closing application-owned services. */
    public fun close() {
        actions.close()
        scope.cancel()
    }

    private suspend fun processActions() {
        for (action in actions) {
            try {
                handle(action)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                unavailable()
            }
        }
    }

    private suspend fun handle(action: MeetingNoteAction) {
        if (handleApprovalAction(action, approvals, mutableState)) return
        when (action) {
            is MeetingNoteAction.Generate -> generate(action)

            is MeetingNoteAction.TitleChanged -> edit { it.copy(title = action.value) }

            is MeetingNoteAction.DecisionChanged -> edit { it.updateDecision(action.index, action.value) }

            is MeetingNoteAction.ActionDescriptionChanged -> edit { draft ->
                draft.updateAction(action.index) {
                    it.copy(description = action.value)
                }
            }

            is MeetingNoteAction.ActionOwnerChanged -> edit { draft ->
                draft.updateAction(action.index) { it.copy(owner = action.value) }
            }

            is MeetingNoteAction.ActionDueDateChanged -> edit { draft ->
                draft.updateAction(action.index) { it.copy(dueDate = action.value) }
            }

            MeetingNoteAction.Save -> save()

            MeetingNoteAction.ProposeWrite,
            MeetingNoteAction.ApproveWrite,
            MeetingNoteAction.DenyWrite,
            MeetingNoteAction.ApprovalDismissed,
            -> error("Approval action was not consumed")

            is MeetingNoteAction.Export -> export(action)

            MeetingNoteAction.ExportDismissed -> mutableState.update { it.copy(export = null) }

            MeetingNoteAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private suspend fun generate(action: MeetingNoteAction.Generate) {
        mutableState.value = MeetingNoteUiState(isBusy = true)
        when (val outcome = notes.generate(action.sourceDocumentId)) {
            is MeetingNoteGenerationOutcome.Generated -> mutableState.value = MeetingNoteUiState(
                note = outcome.note,
                draft = outcome.note.content.toDraft(),
            )

            is MeetingNoteGenerationOutcome.Rejected -> mutableState.value = MeetingNoteUiState(
                notice = MeetingNoteNotice.GenerationRejected(outcome.reason.name),
            )
        }
    }

    private suspend fun save() {
        val current = mutableState.value
        val note = current.note ?: return
        val content = current.draft?.toContentOrNull()
        if (content == null) {
            mutableState.update { it.copy(notice = MeetingNoteNotice.InvalidEdit) }
            return
        }
        mutableState.update { it.copy(isBusy = true, notice = null) }
        when (val outcome = notes.update(note.id, note.revision, content)) {
            is MeetingNoteUpdateOutcome.Updated -> mutableState.update {
                it.copy(
                    note = outcome.note,
                    draft = outcome.note.content.toDraft(),
                    isBusy = false,
                    notice = MeetingNoteNotice.Saved,
                )
            }

            MeetingNoteUpdateOutcome.Conflict,
            MeetingNoteUpdateOutcome.NotFound,
            MeetingNoteUpdateOutcome.StorageUnavailable,
            -> unavailable()
        }
    }

    private suspend fun export(action: MeetingNoteAction.Export) {
        val note = mutableState.value.note ?: return
        mutableState.update { it.copy(isBusy = true, notice = null) }
        when (val outcome = notes.export(note.id, action.format)) {
            is MeetingNoteExportOutcome.Exported -> mutableState.update {
                it.copy(isBusy = false, export = outcome.value)
            }

            MeetingNoteExportOutcome.ContentTooLarge,
            MeetingNoteExportOutcome.NotFound,
            MeetingNoteExportOutcome.StorageUnavailable,
            -> unavailable()
        }
    }

    private fun edit(transform: (MeetingNoteDraft) -> MeetingNoteDraft) {
        mutableState.update { state -> state.draft?.let { state.copy(draft = transform(it), notice = null) } ?: state }
    }

    private fun unavailable() {
        mutableState.update { it.copy(isBusy = false, notice = MeetingNoteNotice.OperationUnavailable) }
    }
}

private suspend fun handleApprovalAction(
    action: MeetingNoteAction,
    approvals: MeetingNoteApprovalPort,
    state: MutableStateFlow<MeetingNoteUiState>,
): Boolean = when (action) {
    MeetingNoteAction.ProposeWrite -> {
        proposeWrite(approvals, state)
        true
    }

    MeetingNoteAction.ApproveWrite -> {
        decideApproval(approvals, state, approve = true)
        true
    }

    MeetingNoteAction.DenyWrite -> {
        decideApproval(approvals, state, approve = false)
        true
    }

    MeetingNoteAction.ApprovalDismissed -> {
        state.update { it.copy(approval = null) }
        true
    }

    else -> false
}

private suspend fun proposeWrite(approvals: MeetingNoteApprovalPort, state: MutableStateFlow<MeetingNoteUiState>) {
    val note = state.value.note ?: return
    state.update { it.copy(isBusy = true, notice = null) }
    when (val result = approvals.propose(note)) {
        is MeetingNoteApprovalProposalResult.Proposed -> state.update {
            it.copy(isBusy = false, approval = result.state)
        }

        MeetingNoteApprovalProposalResult.Unavailable -> state.update {
            it.copy(isBusy = false, notice = MeetingNoteNotice.OperationUnavailable)
        }
    }
}

private suspend fun decideApproval(
    approvals: MeetingNoteApprovalPort,
    state: MutableStateFlow<MeetingNoteUiState>,
    approve: Boolean,
) {
    val current = state.value.approval ?: return
    if (!current.canApprove) return
    state.update { it.copy(approval = current.copy(status = ProposedMeetingNoteStatus.WRITING)) }
    val status = if (approve) approvals.approve() else approvals.deny()
    state.update { it.copy(approval = current.copy(status = status)) }
}

private fun MeetingNoteDraft.updateDecision(index: Int, value: String): MeetingNoteDraft {
    val item = decisions.getOrNull(index) ?: return this
    return copy(decisions = decisions.toMutableList().also { it[index] = item.copy(summary = value) })
}

private fun MeetingNoteDraft.updateAction(
    index: Int,
    transform: (MeetingActionItemDraft) -> MeetingActionItemDraft,
): MeetingNoteDraft {
    val item = actionItems.getOrNull(index) ?: return this
    return copy(actionItems = actionItems.toMutableList().also { it[index] = transform(item) })
}

private fun MeetingNoteDraft.toContentOrNull(): MeetingNoteContent? = try {
    MeetingNoteContent(
        title = title.trim().ifEmpty { null },
        decisions = decisions.map { MeetingDecision(it.summary.trim(), it.sources) },
        actionItems = actionItems.map {
            MeetingActionItem(
                description = it.description.trim(),
                owner = it.owner.trim().ifEmpty { null },
                dueDate = it.dueDate.trim().ifEmpty { null }?.let(::MeetingNoteDate),
                sources = it.sources,
            )
        },
    )
} catch (_: IllegalArgumentException) {
    null
}
