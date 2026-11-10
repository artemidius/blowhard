package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunSnapshot
import kotlinx.coroutines.CancellationException

internal class DurableRunCoordinator(
    private val checkpoints: ConversationRunCheckpoints?,
    private val repository: InMemoryRunRepository,
    private val clock: RunClock,
) {
    suspend fun begin(record: RunRecord): Boolean = try {
        checkpoints?.begin(record)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    @Suppress("TooGenericExceptionCaught") // Adapter failures are intentionally hidden behind the store port.
    suspend fun checkpoint(record: RunRecord, snapshot: RunSnapshot) {
        try {
            checkpoints?.update(record, snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw ConversationCheckpointException(failure)
        }
    }

    suspend fun finish(record: RunRecord, terminal: RunTerminal) {
        val current = record.snapshots.value
        if (current.isTerminal()) return
        val terminalSnapshot = terminal.snapshot(record, current, clock)
        try {
            checkpoint(record, terminalSnapshot)
            repository.finish(record, terminalSnapshot)
        } catch (_: ConversationCheckpointException) {
            fail(record)
        }
    }

    suspend fun fail(record: RunRecord) {
        val current = record.snapshots.value
        if (current.isTerminal()) return
        val failure = RunTerminal.Failed(RunFailure(RunFailureKind.STORAGE_UNAVAILABLE))
        repository.finish(record, failure.snapshot(record, current, clock))
    }
}

internal class ConversationCheckpointException(cause: Throwable) : RuntimeException(cause)
