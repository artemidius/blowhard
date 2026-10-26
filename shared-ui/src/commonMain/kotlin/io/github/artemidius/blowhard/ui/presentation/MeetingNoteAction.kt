package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat

/** User intent accepted by [MeetingNotePresenter]. */
internal sealed interface MeetingNoteAction {
    data class Generate(val sourceDocumentId: DocumentId) : MeetingNoteAction

    data class TitleChanged(val value: String) : MeetingNoteAction

    data class DecisionChanged(val index: Int, val value: String) : MeetingNoteAction

    data class ActionDescriptionChanged(val index: Int, val value: String) : MeetingNoteAction

    data class ActionOwnerChanged(val index: Int, val value: String) : MeetingNoteAction

    data class ActionDueDateChanged(val index: Int, val value: String) : MeetingNoteAction

    data object Save : MeetingNoteAction

    data object ProposeWrite : MeetingNoteAction

    data object ApproveWrite : MeetingNoteAction

    data object DenyWrite : MeetingNoteAction

    data object ApprovalDismissed : MeetingNoteAction

    data class Export(val format: MeetingNoteExportFormat) : MeetingNoteAction

    data object ExportDismissed : MeetingNoteAction

    data object NoticeDismissed : MeetingNoteAction
}
