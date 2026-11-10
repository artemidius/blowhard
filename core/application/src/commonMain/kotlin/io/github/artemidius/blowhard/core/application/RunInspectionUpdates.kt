package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTerminalStatus
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.TokenUsage

internal fun RunInspection.started(at: RunTimestamp): RunInspection = copy(
    events = events.appendBounded(RunInspectionEvent.Started(at)),
)

internal fun RunInspection.withUsage(usage: TokenUsage): RunInspection = copy(usage = usage)

internal fun RunInspection.finished(snapshot: RunSnapshot): RunInspection = copy(
    events = events.appendBounded(snapshot.toInspectionEvent()),
)

private fun RunSnapshot.toInspectionEvent(): RunInspectionEvent.Finished = when (this) {
    is RunSnapshot.Succeeded -> RunInspectionEvent.Finished(updatedAt, RunTerminalStatus.SUCCEEDED)

    is RunSnapshot.Failed -> RunInspectionEvent.Finished(
        at = updatedAt,
        status = RunTerminalStatus.FAILED,
        failureKind = failure.kind,
        retryAdvice = failure.retryAdvice,
        retryAfterSeconds = failure.retryAfterSeconds,
    )

    is RunSnapshot.Cancelled -> RunInspectionEvent.Finished(updatedAt, RunTerminalStatus.CANCELLED)

    is RunSnapshot.Queued, is RunSnapshot.Running -> error("Run inspection requires a terminal snapshot")
}

private fun List<RunInspectionEvent>.appendBounded(event: RunInspectionEvent): List<RunInspectionEvent> =
    (this + event).takeLast(RunInspection.MAX_EVENTS)
