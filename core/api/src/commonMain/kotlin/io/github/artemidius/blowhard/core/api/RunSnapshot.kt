package io.github.artemidius.blowhard.core.api

/** Immutable client-visible state of one generation attempt. */
public sealed interface RunSnapshot {
    /** Identity of the attempt represented by this snapshot. */
    public val runId: RunId

    /** Conversation that owns the attempt. */
    public val conversationId: ConversationId

    /** UTC time at which this immutable snapshot became current. */
    public val updatedAt: RunTimestamp

    /** The attempt exists but provider execution has not started. */
    public data class Queued(
        override val runId: RunId,
        override val conversationId: ConversationId,
        override val updatedAt: RunTimestamp,
    ) : RunSnapshot

    /** The attempt is active and [output] contains the text produced so far. */
    public data class Running(
        override val runId: RunId,
        override val conversationId: ConversationId,
        override val updatedAt: RunTimestamp,
        public val output: String,
    ) : RunSnapshot

    /** The attempt completed successfully with immutable final [output]. */
    public data class Succeeded(
        override val runId: RunId,
        override val conversationId: ConversationId,
        override val updatedAt: RunTimestamp,
        public val output: String,
    ) : RunSnapshot

    /** The attempt stopped because of a safely classified [failure]. */
    public data class Failed(
        override val runId: RunId,
        override val conversationId: ConversationId,
        override val updatedAt: RunTimestamp,
        public val partialOutput: String,
        public val failure: RunFailure,
    ) : RunSnapshot

    /** The attempt was cancelled and will not emit further output. */
    public data class Cancelled(
        override val runId: RunId,
        override val conversationId: ConversationId,
        override val updatedAt: RunTimestamp,
        public val partialOutput: String,
    ) : RunSnapshot
}

/** Client-safe failure details that contain no provider response body or sensitive request data. */
public data class RunFailure(
    public val kind: RunFailureKind,
    public val reference: String? = null,
    public val retryAdvice: RunRetryAdvice = RunRetryAdvice.DO_NOT_RETRY,
    public val retryAfterSeconds: Long? = null,
) {
    init {
        require(retryAfterSeconds == null || retryAfterSeconds >= 0) { "Retry delay must not be negative" }
        require(retryAfterSeconds == null || retryAdvice == RunRetryAdvice.RETRY_AFTER_DELAY) {
            "Retry delay requires delayed retry advice"
        }
    }
}

/** Conservative client guidance for starting a distinct attempt after failure. */
public enum class RunRetryAdvice {
    /** Repeating the same input cannot resolve the failure safely. */
    DO_NOT_RETRY,

    /** The user may explicitly start a new attempt; automatic replay is forbidden. */
    EXPLICIT_NEW_ATTEMPT,

    /** A distinct attempt may be started after [RunFailure.retryAfterSeconds]. */
    RETRY_AFTER_DELAY,
}

/** Stable failure categories exposed to clients. */
public enum class RunFailureKind {
    /** A usable provider credential was unavailable. */
    CREDENTIAL_UNAVAILABLE,

    /** The configured credential was rejected. */
    UNAUTHORIZED,

    /** The generation request was invalid. */
    INVALID_REQUEST,

    /** The provider asked the client to reduce its request rate. */
    RATE_LIMITED,

    /** The provider or network was temporarily unavailable. */
    UNAVAILABLE,

    /** A successful provider response violated the expected streaming contract. */
    INVALID_RESPONSE,

    /** An unexpected, safely redacted failure occurred. */
    UNEXPECTED,
}
