package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.application.ApprovedMeetingNotePreparation
import io.github.artemidius.blowhard.core.application.ApprovedMeetingNoteProposal
import io.github.artemidius.blowhard.core.application.ApprovedMeetingNoteRecovery
import io.github.artemidius.blowhard.core.application.ApprovedMeetingNoteWriter
import io.github.artemidius.blowhard.core.application.ToolApprovalClock
import io.github.artemidius.blowhard.core.application.ToolApprovalDestination
import io.github.artemidius.blowhard.core.application.ToolApprovalId
import io.github.artemidius.blowhard.core.application.ToolApprovalOutcome
import io.github.artemidius.blowhard.core.application.ToolApprovalRunId
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalPort
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalProposalResult
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteStatus
import io.github.artemidius.blowhard.ui.presentation.ProposedMeetingNoteUiState
import io.github.artemidius.blowhard.ui.presentation.UnavailableMeetingNoteApprovalPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Exact-consent boundary for publishing an approved local meeting-note copy. */
public class LocalMeetingNoteApprovalPort private constructor(
    private val writer: ApprovedMeetingNoteWriter,
    private val now: () -> Long,
    private val identity: () -> String,
) : MeetingNoteApprovalPort {
    private var proposal: ApprovedMeetingNoteProposal? = null

    override suspend fun propose(note: MeetingNoteRecord): MeetingNoteApprovalProposalResult {
        val preparedAt = now()
        val copy = note.approvedCopy(identity(), preparedAt)
        return when (
            val prepared = writer.prepare(
                note = copy,
                approvalId = ToolApprovalId(identity()),
                runId = ToolApprovalRunId(identity()),
                callId = ToolCallId(identity()),
                destination = DESTINATION,
                expiresAtEpochMilliseconds = preparedAt + APPROVAL_LIFETIME_MILLISECONDS,
            )
        ) {
            is ApprovedMeetingNotePreparation.Proposed -> prepared.toUiProposal()

            ApprovedMeetingNotePreparation.ContentTooLarge,
            is ApprovedMeetingNotePreparation.Rejected,
            -> MeetingNoteApprovalProposalResult.Unavailable
        }
    }

    override suspend fun approve(): ProposedMeetingNoteStatus {
        val current = proposal ?: return ProposedMeetingNoteStatus.FAILED
        return when (writer.approve(current.binding.id)) {
            ToolApprovalOutcome.ACCEPTED -> execute(current)
            ToolApprovalOutcome.EXPIRED -> ProposedMeetingNoteStatus.EXPIRED
            else -> ProposedMeetingNoteStatus.FAILED
        }
    }

    override suspend fun deny(): ProposedMeetingNoteStatus {
        val current = proposal ?: return ProposedMeetingNoteStatus.FAILED
        return when (writer.deny(current.binding.id)) {
            ToolApprovalOutcome.ACCEPTED -> ProposedMeetingNoteStatus.DENIED
            ToolApprovalOutcome.EXPIRED -> ProposedMeetingNoteStatus.EXPIRED
            else -> ProposedMeetingNoteStatus.FAILED
        }
    }

    private fun ApprovedMeetingNotePreparation.Proposed.toUiProposal(): MeetingNoteApprovalProposalResult.Proposed {
        proposal = value
        return MeetingNoteApprovalProposalResult.Proposed(
            ProposedMeetingNoteUiState(value.note, value.binding.destination.value),
        )
    }

    private suspend fun execute(current: ApprovedMeetingNoteProposal): ProposedMeetingNoteStatus = try {
        when (writer.write(current).outcome) {
            ToolCallOutcome.SUCCEEDED -> ProposedMeetingNoteStatus.COMMITTED
            ToolCallOutcome.REJECTED -> ProposedMeetingNoteStatus.FAILED
            ToolCallOutcome.FAILED -> recover(current)
        }
    } catch (_: CancellationException) {
        withContext(NonCancellable) { recover(current, interrupted = true) }
    }

    private suspend fun recover(
        current: ApprovedMeetingNoteProposal,
        interrupted: Boolean = false,
    ): ProposedMeetingNoteStatus = when (writer.recover(current.operationId)) {
        is ApprovedMeetingNoteRecovery.Committed -> ProposedMeetingNoteStatus.COMMITTED

        ApprovedMeetingNoteRecovery.NotApplied -> if (interrupted) {
            ProposedMeetingNoteStatus.INTERRUPTED
        } else {
            ProposedMeetingNoteStatus.FAILED
        }

        ApprovedMeetingNoteRecovery.Uncertain -> ProposedMeetingNoteStatus.UNCERTAIN
    }

    /** Creates the capability or returns the fail-closed implementation when storage is unavailable. */
    public companion object {
        /** Platform hosts retain ownership of identity and clock generation. */
        public fun create(store: MeetingNoteStore?, now: () -> Long, identity: () -> String): MeetingNoteApprovalPort =
            store?.let {
                LocalMeetingNoteApprovalPort(
                    ApprovedMeetingNoteWriter.create(it, ToolApprovalClock(now)),
                    now,
                    identity,
                )
            } ?: UnavailableMeetingNoteApprovalPort
    }
}

private fun MeetingNoteRecord.approvedCopy(identity: String, timestamp: Long): MeetingNoteRecord = MeetingNoteRecord(
    id = MeetingNoteId(identity),
    sourceDocumentId = sourceDocumentId,
    templateVersion = templateVersion,
    modelId = modelId,
    generatedContent = content,
    editedContent = null,
    createdAtEpochMilliseconds = timestamp,
    updatedAtEpochMilliseconds = timestamp,
    revision = 0,
)

private val DESTINATION = ToolApprovalDestination("Local meeting notes / Approved copies")
private const val APPROVAL_LIFETIME_MILLISECONDS = 5 * 60 * 1_000L
