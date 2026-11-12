package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.MeetingNoteRecord

/** UI-facing boundary for approval-bound note publication. */
public interface MeetingNoteApprovalPort {
    /** Builds an exact proposal without writing it. */
    public suspend fun propose(note: MeetingNoteRecord): MeetingNoteApprovalProposalResult

    /** Approves and executes the currently presented exact proposal. */
    public suspend fun approve(): ProposedMeetingNoteStatus

    /** Denies the currently presented exact proposal without writing it. */
    public suspend fun deny(): ProposedMeetingNoteStatus
}

/** Safe preparation result for the proposed-note view. */
public sealed interface MeetingNoteApprovalProposalResult {
    /** Exact content and destination are ready for user review. */
    public data class Proposed(public val state: ProposedMeetingNoteUiState) : MeetingNoteApprovalProposalResult

    /** No safe proposal could be prepared. */
    public data object Unavailable : MeetingNoteApprovalProposalResult
}

/** Disabled implementation used when the local approval service is unavailable. */
public object UnavailableMeetingNoteApprovalPort : MeetingNoteApprovalPort {
    override suspend fun propose(note: MeetingNoteRecord): MeetingNoteApprovalProposalResult =
        MeetingNoteApprovalProposalResult.Unavailable

    override suspend fun approve(): ProposedMeetingNoteStatus = ProposedMeetingNoteStatus.FAILED

    override suspend fun deny(): ProposedMeetingNoteStatus = ProposedMeetingNoteStatus.FAILED
}
