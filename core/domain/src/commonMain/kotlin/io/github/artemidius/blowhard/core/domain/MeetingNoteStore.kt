package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord

/** Durable meeting-note boundary preserving generated and user-edited values separately. */
public interface MeetingNoteStore {
    /** Inserts a newly generated revision-zero record. */
    public suspend fun insert(note: MeetingNoteRecord): MeetingNoteInsertResult

    /** Atomically inserts one approved note and its replay-safe execution receipt. */
    public suspend fun insertApproved(write: ApprovedMeetingNoteWrite): ApprovedMeetingNoteInsertResult =
        ApprovedMeetingNoteInsertResult.Unavailable

    /** Finds a committed approval receipt without repeating its write. */
    public suspend fun findApprovedOperation(
        operationId: ApprovedNoteWriteOperationId,
    ): ApprovedMeetingNoteReceiptLookup = ApprovedMeetingNoteReceiptLookup.Unavailable

    /** Finds one record. */
    public suspend fun find(id: MeetingNoteId): MeetingNoteStoreLookup

    /** Replaces only user-edited content using optimistic revision control. */
    public suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        editedContent: MeetingNoteContent,
        updatedAtEpochMilliseconds: Long,
    ): MeetingNoteStoreUpdateResult

    /** Releases local storage resources. Repeated calls have no effect. */
    public suspend fun close()
}

/** Durable insert outcome. */
public enum class MeetingNoteInsertResult {
    INSERTED,
    SOURCE_NOT_FOUND,
    ID_CONFLICT,
    UNAVAILABLE,
}

/** Durable lookup outcome. */
public sealed interface MeetingNoteStoreLookup {
    /** A validated record was loaded. */
    public data class Found(public val note: MeetingNoteRecord) : MeetingNoteStoreLookup

    /** No record matched the identity. */
    public data object NotFound : MeetingNoteStoreLookup

    /** Storage could not be read safely. */
    public data object Unavailable : MeetingNoteStoreLookup
}

/** Durable optimistic update outcome. */
public sealed interface MeetingNoteStoreUpdateResult {
    /** The updated record was read back successfully. */
    public data class Updated(public val note: MeetingNoteRecord) : MeetingNoteStoreUpdateResult

    /** No record matched the identity. */
    public data object NotFound : MeetingNoteStoreUpdateResult

    /** The expected revision was stale. */
    public data object Conflict : MeetingNoteStoreUpdateResult

    /** Storage could not complete the operation. */
    public data object Unavailable : MeetingNoteStoreUpdateResult
}
