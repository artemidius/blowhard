package io.github.artemidius.blowhard.core.api

/** Content-free operational view of a generation attempt. */
public data class RunInspection(
    public val runId: RunId,
    public val conversationId: ConversationId,
    public val target: GenerationTarget,
    public val parameters: GenerationParameters,
    public val events: List<RunInspectionEvent>,
    public val usage: TokenUsage? = null,
) {
    init {
        require(events.isNotEmpty()) { "Run inspection requires at least one event" }
        require(events.size <= MAX_EVENTS) { "Run inspection event limit exceeded" }
        require(
            events.zipWithNext().all { (first, second) ->
                first.at.epochMilliseconds <= second.at.epochMilliseconds
            },
        ) {
            "Run inspection events must be chronological"
        }
    }

    /** Maximum event count exposed for one run inspection. */
    public companion object {
        public const val MAX_EVENTS: Int = 32
    }
}

/** Read-only capability for inspecting allowlisted operational run metadata. */
public fun interface RunInspector {
    /** Returns a content-free inspection for [runId] without starting provider work. */
    public suspend fun inspect(runId: RunId): RunInspectionResult
}

/** Result of looking up one process-owned run inspection. */
public sealed interface RunInspectionResult {
    /** The requested run and its allowlisted operational metadata. */
    public data class Found(public val inspection: RunInspection) : RunInspectionResult

    /** No process-owned run matched the requested identity. */
    public data object NotFound : RunInspectionResult
}

/** One bounded, content-free lifecycle event in a run inspection. */
public sealed interface RunInspectionEvent {
    /** UTC time at which this event occurred. */
    public val at: RunTimestamp

    /** The application accepted and identified the attempt. */
    public data class Accepted(override val at: RunTimestamp) : RunInspectionEvent

    /** Provider execution started after application concurrency admission. */
    public data class Started(override val at: RunTimestamp) : RunInspectionEvent

    /** The attempt reached one immutable terminal [status]. */
    public data class Finished(
        override val at: RunTimestamp,
        public val status: RunTerminalStatus,
        public val failureKind: RunFailureKind? = null,
        public val retryAdvice: RunRetryAdvice? = null,
        public val retryAfterSeconds: Long? = null,
    ) : RunInspectionEvent {
        init {
            require((status == RunTerminalStatus.FAILED) == (failureKind != null)) {
                "Only failed inspections carry a failure kind"
            }
            require((failureKind != null) == (retryAdvice != null)) {
                "Failure kind and retry advice must be present together"
            }
            require(retryAfterSeconds == null || retryAfterSeconds >= 0) {
                "Retry delay must not be negative"
            }
            require(retryAfterSeconds == null || retryAdvice == RunRetryAdvice.RETRY_AFTER_DELAY) {
                "Retry delay requires delayed retry advice"
            }
        }
    }
}

/** Terminal status exposed without generated content. */
public enum class RunTerminalStatus {
    /** Provider generation completed successfully. */
    SUCCEEDED,

    /** Generation ended with a safely classified failure. */
    FAILED,

    /** The user or owning lifecycle cancelled generation. */
    CANCELLED,
}

/** Optional provider-reported token counts; absent values remain unknown rather than zero. */
public data class TokenUsage(
    public val inputTokens: Long? = null,
    public val outputTokens: Long? = null,
    public val totalTokens: Long? = null,
) {
    init {
        require(inputTokens != null || outputTokens != null || totalTokens != null) {
            "Token usage must contain at least one reported count"
        }
        require(listOfNotNull(inputTokens, outputTokens, totalTokens).all { it >= 0 }) {
            "Token counts must not be negative"
        }
    }
}
