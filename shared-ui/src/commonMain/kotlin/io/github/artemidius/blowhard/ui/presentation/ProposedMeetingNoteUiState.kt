package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.MeetingNoteRecord

/** Safe presentation state for one exact local-note mutation awaiting user consent. */
public data class ProposedMeetingNoteUiState(
    public val note: MeetingNoteRecord,
    public val destination: String,
    public val status: ProposedMeetingNoteStatus = ProposedMeetingNoteStatus.PENDING,
) {
    init {
        require(destination.isNotBlank() && destination.length <= MAXIMUM_DESTINATION_CHARACTERS) {
            "Proposed note destination is invalid"
        }
    }

    /** Whether affirmative consent is still available. */
    public val canApprove: Boolean get() = status == ProposedMeetingNoteStatus.PENDING

    /** Whether explicit denial is still available. */
    public val canDeny: Boolean get() = status == ProposedMeetingNoteStatus.PENDING
}

/** User-visible lifecycle for a proposed note write. */
public enum class ProposedMeetingNoteStatus {
    PENDING,
    WRITING,
    COMMITTED,
    DENIED,
    EXPIRED,
    INTERRUPTED,
    UNCERTAIN,
    FAILED,
}

private const val MAXIMUM_DESTINATION_CHARACTERS = 512
