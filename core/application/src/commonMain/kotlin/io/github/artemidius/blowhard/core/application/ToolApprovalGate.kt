package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolCallResult
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmInline

/** Stable application-owned identity for one explicit mutation approval. */
@JvmInline
public value class ToolApprovalId(public val value: String) {
    init {
        require(
            value.isNotBlank() &&
                value.length <= MAXIMUM_APPROVAL_ID_CHARACTERS &&
                value.none(Char::isISOControl),
        ) {
            "Tool approval identity is invalid"
        }
    }
}

/** Human-readable mutation target shown before consent. */
@JvmInline
public value class ToolApprovalDestination(public val value: String) {
    init {
        require(
            value.isNotBlank() &&
                value.length <= MAXIMUM_APPROVAL_DESTINATION_CHARACTERS &&
                value.none(Char::isISOControl),
        ) {
            "Tool approval destination is invalid"
        }
    }
}

/** Exact immutable values covered by one approval request. */
public data class ToolApprovalBinding(
    public val id: ToolApprovalId,
    public val proposal: ToolCallProposal,
    public val destination: ToolApprovalDestination,
    public val expiresAtEpochMilliseconds: Long,
) {
    init {
        require(expiresAtEpochMilliseconds >= 0) { "Tool approval expiry is invalid" }
    }
}

/** Observable lifecycle of an approval without exposing executor details. */
public enum class ToolApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CONSUMED,
}

/** Safe result for approval lifecycle operations. */
public enum class ToolApprovalOutcome {
    ACCEPTED,
    UNKNOWN_TOOL,
    NOT_MUTATING,
    INVALID_ARGUMENTS,
    DUPLICATE_ID,
    NOT_FOUND,
    NOT_PENDING,
    EXPIRED,
}

/** Monotonic-enough wall-clock boundary supplied by the platform composition root. */
public fun interface ToolApprovalClock {
    /** Returns current UTC epoch milliseconds for expiry decisions. */
    public fun nowEpochMilliseconds(): Long
}

/**
 * Fail-closed gate for explicitly approved mutation tools.
 *
 * An approval is bound to the complete call arguments, target, identity, and expiry. Execution consumes the
 * approval before invoking the side effect, so interruption cannot silently replay the mutation.
 */
public class ToolApprovalGate private constructor(
    registrations: List<ApprovedToolRegistration>,
    private val clock: ToolApprovalClock,
) {
    private val tools: Map<ToolName, ApprovedToolEntry> = registrations.associate { registration ->
        registration.tool.definition.name to ApprovedToolEntry(
            registration.tool,
            requireNotNull(StrictToolInputSchema.parse(registration.tool.definition.inputSchemaJson)),
        )
    }
    private val approvals = mutableMapOf<ToolApprovalId, ApprovalRecord>()
    private val access = Mutex()

    /** Registers a new pending approval only for a known mutating tool with schema-valid arguments. */
    public suspend fun request(binding: ToolApprovalBinding): ToolApprovalOutcome = access.withLock {
        val tool = tools[binding.proposal.name] ?: return@withLock ToolApprovalOutcome.UNKNOWN_TOOL
        if (!tool.registration.definition.mutating) return@withLock ToolApprovalOutcome.NOT_MUTATING
        if (!tool.schema.accepts(binding.proposal.argumentsJson)) return@withLock ToolApprovalOutcome.INVALID_ARGUMENTS
        if (binding.id in approvals) return@withLock ToolApprovalOutcome.DUPLICATE_ID
        if (binding.isExpired(clock)) return@withLock ToolApprovalOutcome.EXPIRED
        approvals[binding.id] = ApprovalRecord(binding, ToolApprovalStatus.PENDING)
        ToolApprovalOutcome.ACCEPTED
    }

    /** Records explicit consent for the exact previously presented binding. */
    public suspend fun approve(id: ToolApprovalId): ToolApprovalOutcome = decide(id, ToolApprovalStatus.APPROVED)

    /** Records explicit denial; denied approvals can never be reused. */
    public suspend fun deny(id: ToolApprovalId): ToolApprovalOutcome = decide(id, ToolApprovalStatus.DENIED)

    /** Returns the current safe lifecycle state, expiring stale pending approvals on observation. */
    public suspend fun status(id: ToolApprovalId): ToolApprovalStatus? = access.withLock {
        approvals[id]?.also { record -> record.expireIfNeeded(clock) }?.status
    }

    /** Executes only an approved binding and consumes consent before crossing the mutation boundary. */
    public suspend fun execute(binding: ToolApprovalBinding): ToolCallResult {
        val resolution = access.withLock { resolve(binding) }
        return when (resolution) {
            is ApprovalExecution.Rejected -> rejected(binding.proposal, resolution.error)
            is ApprovalExecution.Ready -> execute(resolution.entry, binding.proposal)
        }
    }

    private fun resolve(binding: ToolApprovalBinding): ApprovalExecution {
        val record = approvals[binding.id] ?: return ApprovalExecution.Rejected("approval_not_found")
        record.expireIfNeeded(clock)
        return when {
            record.binding != binding -> ApprovalExecution.Rejected("approval_mismatch")

            record.status == ToolApprovalStatus.EXPIRED -> ApprovalExecution.Rejected("approval_expired")

            record.status != ToolApprovalStatus.APPROVED -> ApprovalExecution.Rejected("approval_not_granted")

            else -> {
                record.status = ToolApprovalStatus.CONSUMED
                ApprovalExecution.Ready(tools.getValue(binding.proposal.name))
            }
        }
    }

    private suspend fun execute(entry: ApprovedToolEntry, proposal: ToolCallProposal): ToolCallResult {
        val execution = try {
            entry.registration.executor.execute(proposal.argumentsJson)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ToolExecutionResult.Failed("{\"error\":\"execution_failed\"}")
        }
        return execution.approvedResult(proposal)
    }

    private suspend fun decide(id: ToolApprovalId, decision: ToolApprovalStatus): ToolApprovalOutcome =
        access.withLock {
            val record = approvals[id] ?: return@withLock ToolApprovalOutcome.NOT_FOUND
            record.expireIfNeeded(clock)
            when (record.status) {
                ToolApprovalStatus.PENDING -> {
                    record.status = decision
                    ToolApprovalOutcome.ACCEPTED
                }

                ToolApprovalStatus.EXPIRED -> ToolApprovalOutcome.EXPIRED

                else -> ToolApprovalOutcome.NOT_PENDING
            }
        }

    /** Validated construction for the isolated mutation registry. */
    public companion object {
        /** Creates a gate only for a non-empty, unique set of supported strict schemas. */
        public fun create(registrations: List<ApprovedToolRegistration>, clock: ToolApprovalClock): ToolApprovalGate? {
            val names = registrations.map { it.tool.definition.name }
            return registrations.takeIf { tools ->
                tools.isNotEmpty() && names.distinct().size == names.size && tools.all { registration ->
                    StrictToolInputSchema.parse(registration.tool.definition.inputSchemaJson) != null
                }
            }?.let { ToolApprovalGate(it, clock) }
        }
    }
}

/** Mutation registration kept separate from the ordinary read-only registry. */
public data class ApprovedToolRegistration(public val tool: RegisteredTool)

private data class ApprovedToolEntry(val registration: RegisteredTool, val schema: StrictToolInputSchema)

private sealed interface ApprovalExecution {
    data class Ready(val entry: ApprovedToolEntry) : ApprovalExecution
    data class Rejected(val error: String) : ApprovalExecution
}

private data class ApprovalRecord(val binding: ToolApprovalBinding, var status: ToolApprovalStatus) {
    fun expireIfNeeded(clock: ToolApprovalClock) {
        val expirable = status == ToolApprovalStatus.PENDING || status == ToolApprovalStatus.APPROVED
        if (expirable && binding.isExpired(clock)) {
            status = ToolApprovalStatus.EXPIRED
        }
    }
}

private fun ToolApprovalBinding.isExpired(clock: ToolApprovalClock): Boolean =
    clock.nowEpochMilliseconds() >= expiresAtEpochMilliseconds

private fun ToolExecutionResult.approvedResult(proposal: ToolCallProposal): ToolCallResult {
    val outcome = when (this) {
        is ToolExecutionResult.Completed -> ToolCallOutcome.SUCCEEDED
        is ToolExecutionResult.Rejected -> ToolCallOutcome.REJECTED
        is ToolExecutionResult.Failed -> ToolCallOutcome.FAILED
    }
    val output = when (this) {
        is ToolExecutionResult.Completed -> outputJson
        is ToolExecutionResult.Rejected -> outputJson
        is ToolExecutionResult.Failed -> outputJson
    }
    return ToolCallResult(proposal.id, proposal.name, outcome, output)
}

private fun rejected(proposal: ToolCallProposal, error: String): ToolCallResult = ToolCallResult(
    proposal.id,
    proposal.name,
    ToolCallOutcome.REJECTED,
    "{\"error\":\"$error\"}",
)

private const val MAXIMUM_APPROVAL_ID_CHARACTERS = 128
private const val MAXIMUM_APPROVAL_DESTINATION_CHARACTERS = 512
