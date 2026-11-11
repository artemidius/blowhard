package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteInsertResult
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteReceiptLookup
import io.github.artemidius.blowhard.core.domain.ApprovedMeetingNoteWrite
import io.github.artemidius.blowhard.core.domain.ApprovedNoteWriteOperationId
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteStoreLookup
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallResult
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Exact note preview and approval binding presented before a local write. */
public data class ApprovedMeetingNoteProposal(
    public val operationId: ApprovedNoteWriteOperationId,
    public val binding: ToolApprovalBinding,
    public val note: MeetingNoteRecord,
)

/** Safe preparation result; no storage write occurs during this phase. */
public sealed interface ApprovedMeetingNotePreparation {
    /** A schema-valid proposal is waiting for explicit consent. */
    public data class Proposed(public val value: ApprovedMeetingNoteProposal) : ApprovedMeetingNotePreparation

    /** The note could not be represented inside the bounded tool protocol. */
    public data object ContentTooLarge : ApprovedMeetingNotePreparation

    /** Approval infrastructure refused the proposal. */
    public data class Rejected(public val reason: ToolApprovalOutcome) : ApprovedMeetingNotePreparation
}

/** Recovery result for a write whose final response may have been interrupted. */
public sealed interface ApprovedMeetingNoteRecovery {
    /** The deterministic note identity exists; callers must not repeat the write. */
    public data class Committed(public val note: MeetingNoteRecord) : ApprovedMeetingNoteRecovery

    /** No note exists under the deterministic identity. */
    public data object NotApplied : ApprovedMeetingNoteRecovery

    /** Storage could not prove either outcome. */
    public data object Uncertain : ApprovedMeetingNoteRecovery
}

/**
 * Approval-bound local meeting-note writer with deterministic crash recovery.
 *
 * Prepared records remain immutable and in memory. Restart never replays a pending side effect: callers recover the
 * deterministic note identity and present the result instead.
 */
public class ApprovedMeetingNoteWriter private constructor(
    private val store: MeetingNoteStore,
    private val gate: ToolApprovalGate,
    private val pending: MutableMap<MeetingNoteId, PendingNote>,
    private val access: Mutex,
) {
    /** Creates and registers a complete approval proposal without writing to storage. */
    public suspend fun prepare(
        note: MeetingNoteRecord,
        approvalId: ToolApprovalId,
        runId: ToolApprovalRunId,
        callId: ToolCallId,
        destination: ToolApprovalDestination,
        expiresAtEpochMilliseconds: Long,
    ): ApprovedMeetingNotePreparation {
        val payload = MeetingNoteMutationCodec.encode(note)
        val arguments = mutationArguments(note.id, payload)
        val proposal = runCatching {
            ToolApprovalBinding(
                approvalId,
                runId,
                io.github.artemidius.blowhard.core.domain.ToolCallProposal(callId, TOOL_NAME, arguments),
                destination,
                expiresAtEpochMilliseconds,
            )
        }.getOrNull() ?: return ApprovedMeetingNotePreparation.ContentTooLarge
        val operationId = ApprovedNoteWriteOperationId(approvalId.value)
        access.withLock { pending[note.id] = PendingNote(note, payload, proposal, operationId) }
        val outcome = gate.request(proposal)
        return if (outcome == ToolApprovalOutcome.ACCEPTED) {
            ApprovedMeetingNotePreparation.Proposed(ApprovedMeetingNoteProposal(operationId, proposal, note))
        } else {
            access.withLock { pending.remove(note.id) }
            ApprovedMeetingNotePreparation.Rejected(outcome)
        }
    }

    /** Records explicit consent for the proposal identity. */
    public suspend fun approve(id: ToolApprovalId): ToolApprovalOutcome = gate.approve(id)

    /** Records explicit denial for the proposal identity. */
    public suspend fun deny(id: ToolApprovalId): ToolApprovalOutcome = gate.deny(id)

    /** Writes exactly the approved immutable proposal and consumes consent before storage access. */
    public suspend fun write(proposal: ApprovedMeetingNoteProposal): ToolCallResult = gate.execute(proposal.binding)

    /** Resolves an interrupted write by deterministic identity without retrying it. */
    public suspend fun recover(operationId: ApprovedNoteWriteOperationId): ApprovedMeetingNoteRecovery =
        when (val receipt = store.findApprovedOperation(operationId)) {
            is ApprovedMeetingNoteReceiptLookup.Committed -> recoverCommitted(receipt.noteId)
            ApprovedMeetingNoteReceiptLookup.NotFound -> ApprovedMeetingNoteRecovery.NotApplied
            ApprovedMeetingNoteReceiptLookup.Unavailable -> ApprovedMeetingNoteRecovery.Uncertain
        }

    private suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val arguments = parseMutationArguments(argumentsJson)
        val prepared = arguments?.let { value -> access.withLock { pending.remove(value.id) } }
        return when {
            arguments == null -> rejected("invalid_arguments")
            prepared == null -> rejected("proposal_not_available")
            prepared.payload != arguments.payload -> rejected("proposal_mismatch")
            else -> insert(prepared)
        }
    }

    private suspend fun insert(prepared: PendingNote): ToolExecutionResult {
        val binding = prepared.binding
        val write = ApprovedMeetingNoteWrite(
            prepared.operationId,
            binding.id.value,
            binding.runId.value,
            binding.proposal.id.value,
            binding.proposal.argumentsJson,
            prepared.note,
        )
        return when (val result = store.insertApproved(write)) {
            ApprovedMeetingNoteInsertResult.Inserted -> committed(prepared.note.id)

            is ApprovedMeetingNoteInsertResult.AlreadyCommitted -> if (result.noteId == prepared.note.id) {
                committed(prepared.note.id)
            } else {
                rejected("operation_id_conflict")
            }

            ApprovedMeetingNoteInsertResult.SourceNotFound -> rejected("source_not_found")

            ApprovedMeetingNoteInsertResult.IdConflict -> rejected("note_id_conflict")

            ApprovedMeetingNoteInsertResult.Unavailable -> failed("write_outcome_uncertain")
        }
    }

    private suspend fun recoverCommitted(noteId: MeetingNoteId): ApprovedMeetingNoteRecovery =
        when (val lookup = store.find(noteId)) {
            is MeetingNoteStoreLookup.Found -> ApprovedMeetingNoteRecovery.Committed(lookup.note)
            MeetingNoteStoreLookup.NotFound -> ApprovedMeetingNoteRecovery.Uncertain
            MeetingNoteStoreLookup.Unavailable -> ApprovedMeetingNoteRecovery.Uncertain
        }

    /** Validated composition for the isolated mutation tool. */
    public companion object {
        /** Creates a writer whose only mutation is one exact local meeting-note insert. */
        public fun create(store: MeetingNoteStore, clock: ToolApprovalClock): ApprovedMeetingNoteWriter {
            val pending = mutableMapOf<MeetingNoteId, PendingNote>()
            val access = Mutex()
            lateinit var writer: ApprovedMeetingNoteWriter
            val registration = ApprovedToolRegistration(
                RegisteredTool(
                    ToolDefinition(
                        TOOL_NAME,
                        "Write one approved meeting note to local application storage",
                        TOOL_SCHEMA,
                        mutating = true,
                    ),
                ) { arguments -> writer.execute(arguments) },
            )
            val gate = requireNotNull(ToolApprovalGate.create(listOf(registration), clock))
            writer = ApprovedMeetingNoteWriter(store, gate, pending, access)
            return writer
        }
    }
}

private data class PendingNote(
    val note: MeetingNoteRecord,
    val payload: String,
    val binding: ToolApprovalBinding,
    val operationId: ApprovedNoteWriteOperationId,
)
private data class MutationArguments(val id: MeetingNoteId, val payload: String)

private fun mutationArguments(id: MeetingNoteId, payload: String): String = JsonObject(
    mapOf("note_id" to JsonPrimitive(id.value), "payload" to JsonPrimitive(payload)),
).toString()

private fun parseMutationArguments(raw: String): MutationArguments? = runCatching {
    val value = Json.parseToJsonElement(raw) as JsonObject
    val id = (value["note_id"] as JsonPrimitive).content
    val payload = (value["payload"] as JsonPrimitive).content
    MutationArguments(MeetingNoteId(id), payload)
}.getOrNull()

private fun committed(id: MeetingNoteId): ToolExecutionResult = ToolExecutionResult.Completed(
    JsonObject(mapOf("note_id" to JsonPrimitive(id.value), "status" to JsonPrimitive("committed"))).toString(),
)

private val TOOL_NAME = ToolName("write_meeting_note")
private const val TOOL_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"note_id":{"type":"string"},"payload":{"type":"string"}},"required":["note_id","payload"]}"""
