package io.github.artemidius.blowhard.core.api

/** Result of requesting cancellation of a run. */
public enum class CancelRunResult {
    /** Cancellation was accepted for an active run. */
    ACCEPTED,

    /** The run already reached a terminal state. */
    ALREADY_FINISHED,

    /** The run does not exist or is no longer retained. */
    NOT_FOUND,
}
