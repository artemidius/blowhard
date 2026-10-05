package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryRunRepository(private val clock: RunClock) {
    private val mutex = Mutex()
    private val runs = mutableMapOf<RunId, RunRecord>()
    private val activeConversations = mutableSetOf<ConversationId>()
    private val deletingConversations = mutableSetOf<ConversationId>()
    private var closed = false

    suspend fun add(record: RunRecord): AddRunResult = mutex.withLock {
        when {
            closed -> AddRunResult.Closed

            record.conversationId in activeConversations || record.conversationId in deletingConversations ->
                AddRunResult.ConversationBusy

            else -> {
                runs[record.runId] = record
                activeConversations += record.conversationId
                AddRunResult.Added
            }
        }
    }

    suspend fun find(runId: RunId): RunRecord? = mutex.withLock { runs[runId] }

    suspend fun discard(record: RunRecord): Boolean = mutex.withLock {
        if (record.snapshots.value !is RunSnapshot.Queued || record.job != null) {
            false
        } else if (runs[record.runId] !== record) {
            false
        } else {
            activeConversations -= record.conversationId
            runs.remove(record.runId)
            true
        }
    }

    suspend fun markRunning(record: RunRecord): Boolean = updateActive(record) { current ->
        RunSnapshot.Running(record.runId, record.conversationId, clock.now(), current.output())
    }

    suspend fun append(record: RunRecord, text: String): Boolean = updateActive(record) { current ->
        RunSnapshot.Running(record.runId, record.conversationId, clock.now(), current.output() + text)
    }

    suspend fun finish(record: RunRecord, terminalSnapshot: RunSnapshot): Boolean = mutex.withLock {
        require(terminalSnapshot.isTerminal()) { "Run finish requires a terminal snapshot" }
        require(terminalSnapshot.runId == record.runId) { "Terminal snapshot run identity must match" }
        require(terminalSnapshot.conversationId == record.conversationId) {
            "Terminal snapshot conversation identity must match"
        }
        val current = record.snapshots.value
        if (current.isTerminal()) {
            false
        } else {
            activeConversations -= record.conversationId
            record.snapshots.value = terminalSnapshot
            true
        }
    }

    suspend fun requestCancellation(runId: RunId): CancellationDecision = mutex.withLock {
        val record = runs[runId] ?: return@withLock CancellationDecision.NotFound
        when {
            record.snapshots.value.isTerminal() -> CancellationDecision.AlreadyFinished(record.job)

            record.cancellationRequested -> CancellationDecision.AlreadyRequested(record, record.job)

            else -> {
                record.cancellationRequested = true
                CancellationDecision.Accepted(record, checkNotNull(record.job))
            }
        }
    }

    suspend fun updateDeletion(conversationId: ConversationId, operation: DeletionOperation): DeletionDecision =
        mutex.withLock {
            when (operation) {
                DeletionOperation.Reserve -> when {
                    closed -> DeletionDecision.Closed

                    conversationId in activeConversations || conversationId in deletingConversations ->
                        DeletionDecision.ConversationBusy

                    else -> {
                        deletingConversations += conversationId
                        DeletionDecision.Reserved
                    }
                }

                is DeletionOperation.Release -> {
                    check(deletingConversations.remove(conversationId)) { "Conversation deletion was not reserved" }
                    if (operation.discardRuns) {
                        runs.entries.removeAll { (_, record) -> record.conversationId == conversationId }
                    }
                    DeletionDecision.Released
                }
            }
        }

    suspend fun close(): List<RunRecord> = mutex.withLock {
        closed = true
        runs.values.filterNot { it.snapshots.value.isTerminal() }.onEach { it.cancellationRequested = true }
    }

    private suspend fun updateActive(record: RunRecord, transform: (RunSnapshot) -> RunSnapshot): Boolean =
        mutex.withLock {
            val current = record.snapshots.value
            if (current.isTerminal() || record.cancellationRequested) {
                false
            } else {
                record.snapshots.value = transform(current)
                true
            }
        }
}

internal sealed interface RunTerminal {
    data object Succeeded : RunTerminal

    data class Failed(val failure: RunFailure) : RunTerminal

    data object Cancelled : RunTerminal

    data object Unexpected : RunTerminal
}

internal fun RunTerminal.snapshot(record: RunRecord, current: RunSnapshot, clock: RunClock): RunSnapshot = when (this) {
    RunTerminal.Succeeded ->
        RunSnapshot.Succeeded(record.runId, record.conversationId, clock.now(), current.output())

    is RunTerminal.Failed ->
        RunSnapshot.Failed(record.runId, record.conversationId, clock.now(), current.output(), failure)

    RunTerminal.Cancelled ->
        RunSnapshot.Cancelled(record.runId, record.conversationId, clock.now(), current.output())

    RunTerminal.Unexpected -> if (record.cancellationRequested) {
        RunSnapshot.Cancelled(record.runId, record.conversationId, clock.now(), current.output())
    } else {
        RunSnapshot.Failed(
            record.runId,
            record.conversationId,
            clock.now(),
            current.output(),
            RunFailure(RunFailureKind.UNEXPECTED),
        )
    }
}

internal sealed interface AddRunResult {
    data object Added : AddRunResult

    data object ConversationBusy : AddRunResult

    data object Closed : AddRunResult
}

internal sealed interface CancellationDecision {
    data class Accepted(val record: RunRecord, val job: Job) : CancellationDecision

    data class AlreadyRequested(val record: RunRecord, val job: Job?) : CancellationDecision

    data class AlreadyFinished(val job: Job?) : CancellationDecision

    data object NotFound : CancellationDecision
}

internal sealed interface DeletionDecision {
    data object Reserved : DeletionDecision

    data object ConversationBusy : DeletionDecision

    data object Closed : DeletionDecision

    data object Released : DeletionDecision
}

internal sealed interface DeletionOperation {
    data object Reserve : DeletionOperation

    data class Release(val discardRuns: Boolean) : DeletionOperation
}
