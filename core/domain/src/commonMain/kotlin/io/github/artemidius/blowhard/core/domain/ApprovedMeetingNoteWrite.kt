package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import kotlin.jvm.JvmInline

/** Durable identity used to reconcile one approval-bound write after interruption. */
@JvmInline
public value class ApprovedNoteWriteOperationId(public val value: String) {
    init {
        require(value.isNotBlank() && value.length <= MAX_ID_CHARACTERS && value.none(Char::isISOControl)) {
            "Approved note-write operation identity is invalid"
        }
    }
}

/** Exact immutable write and consent identities committed in one local transaction. */
public data class ApprovedMeetingNoteWrite(
    public val operationId: ApprovedNoteWriteOperationId,
    public val approvalId: String,
    public val runId: String,
    public val callId: String,
    public val argumentsJson: String,
    public val note: MeetingNoteRecord,
) {
    init {
        require(approvalId.isBoundedIdentity()) { "Approval identity is invalid" }
        require(runId.isBoundedIdentity()) { "Approval run identity is invalid" }
        require(callId.isBoundedIdentity()) { "Approval call identity is invalid" }
        require(argumentsJson.isNotBlank() && argumentsJson.length <= MAX_ARGUMENT_CHARACTERS) {
            "Approved note-write arguments are invalid"
        }
    }
}

/** Transactional write result with replay-safe committed receipt handling. */
public sealed interface ApprovedMeetingNoteInsertResult {
    /** Note and receipt were committed atomically. */
    public data object Inserted : ApprovedMeetingNoteInsertResult

    /** The same operation was already committed; no second note was inserted. */
    public data class AlreadyCommitted(public val noteId: MeetingNoteId) : ApprovedMeetingNoteInsertResult

    /** The source extraction no longer exists. */
    public data object SourceNotFound : ApprovedMeetingNoteInsertResult

    /** The requested note identity belongs to another operation. */
    public data object IdConflict : ApprovedMeetingNoteInsertResult

    /** The operation identity is already bound to different consent or arguments. */
    public data object OperationConflict : ApprovedMeetingNoteInsertResult

    /** Storage cannot prove whether the transaction committed. */
    public data object Unavailable : ApprovedMeetingNoteInsertResult
}

/** Durable receipt lookup used during restart recovery. */
public sealed interface ApprovedMeetingNoteReceiptLookup {
    /** A committed operation resolves to one deterministic note identity. */
    public data class Committed(public val noteId: MeetingNoteId) : ApprovedMeetingNoteReceiptLookup

    /** No committed receipt exists for the operation. */
    public data object NotFound : ApprovedMeetingNoteReceiptLookup

    /** Storage could not determine receipt state. */
    public data object Unavailable : ApprovedMeetingNoteReceiptLookup
}

private fun String.isBoundedIdentity(): Boolean =
    isNotBlank() && length <= MAX_ID_CHARACTERS && none(Char::isISOControl)

private const val MAX_ID_CHARACTERS = 128
private const val MAX_ARGUMENT_CHARACTERS = 32 * 1_024
