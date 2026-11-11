package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteExport
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference

/** Editable, immutable presentation state for one generated meeting note. */
public data class MeetingNoteUiState(
    public val note: MeetingNoteRecord? = null,
    public val draft: MeetingNoteDraft? = null,
    public val isBusy: Boolean = false,
    public val notice: MeetingNoteNotice? = null,
    public val export: MeetingNoteExport? = null,
)

/** String-backed editor state that permits incomplete values while the user types. */
public data class MeetingNoteDraft(
    public val title: String,
    public val decisions: List<MeetingDecisionDraft>,
    public val actionItems: List<MeetingActionItemDraft>,
)

/** Editable decision text with immutable generated evidence. */
public data class MeetingDecisionDraft(
    public val summary: String,
    public val sources: List<MeetingNoteSourceReference>,
)

/** Editable action fields with immutable generated evidence. */
public data class MeetingActionItemDraft(
    public val description: String,
    public val owner: String,
    public val dueDate: String,
    public val sources: List<MeetingNoteSourceReference>,
)

/** Sanitized feedback for the typed generation and edit workflow. */
public sealed interface MeetingNoteNotice {
    /** The editable revision was stored locally. */
    public data object Saved : MeetingNoteNotice

    /** One or more draft fields violate the typed note contract. */
    public data object InvalidEdit : MeetingNoteNotice

    /** Structured generation ended with a sanitized provider-neutral label. */
    public data class GenerationRejected(public val label: String) : MeetingNoteNotice

    /** The requested local operation could not complete safely. */
    public data object OperationUnavailable : MeetingNoteNotice
}

internal fun MeetingNoteContent.toDraft(): MeetingNoteDraft = MeetingNoteDraft(
    title = title.orEmpty(),
    decisions = decisions.map { MeetingDecisionDraft(it.summary, it.sources) },
    actionItems = actionItems.map {
        MeetingActionItemDraft(it.description, it.owner.orEmpty(), it.dueDate?.value.orEmpty(), it.sources)
    },
)
