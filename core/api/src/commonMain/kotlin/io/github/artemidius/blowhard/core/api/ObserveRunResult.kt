package io.github.artemidius.blowhard.core.api

import kotlinx.coroutines.flow.Flow

/** Result of looking up a run for observation. */
public sealed interface ObserveRunResult {
    /**
     * Exposes snapshots of one existing attempt.
     *
     * Collecting [snapshots] does not start generation, and cancelling a collector does not cancel
     * the underlying run. Use [ConversationRuns.cancel] to request run cancellation.
     */
    public class Found(public val snapshots: Flow<RunSnapshot>) : ObserveRunResult

    /** The requested run does not exist or is no longer retained. */
    public data object NotFound : ObserveRunResult
}
