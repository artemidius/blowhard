package io.github.artemidius.blowhard.core.api

/**
 * Client-facing capability for starting, observing, and cancelling conversation runs.
 *
 * Starting a run is the only operation that may initiate generation. Observing an existing run
 * never starts or repeats provider work, and multiple observers must see the same attempt.
 */
public interface ConversationRuns {
    /** Validates [request] and attempts to create exactly one new run. */
    public suspend fun start(request: StartRunRequest): StartRunResult

    /** Finds the state stream for [runId] without initiating work. */
    public suspend fun observe(runId: RunId): ObserveRunResult

    /** Requests cancellation of [runId] and waits until the request is accepted or rejected. */
    public suspend fun cancel(runId: RunId): CancelRunResult
}
