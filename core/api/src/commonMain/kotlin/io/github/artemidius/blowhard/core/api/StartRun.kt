package io.github.artemidius.blowhard.core.api

/** Untrusted client input used to request a new generation attempt. */
public data class StartRunRequest(public val conversationId: ConversationId, public val prompt: String)

/** Result of asking the application to start a run. */
public sealed interface StartRunResult {
    /** A new attempt was created with immutable [target] and can be observed using [runId]. */
    public data class Started(public val runId: RunId, public val target: GenerationTarget = GenerationTarget.Demo) :
        StartRunResult

    /** No attempt was created because the request violated an application rule. */
    public data class Rejected(public val reason: StartRunRejection) : StartRunResult
}

/** Stable reasons a start request can be rejected before provider work begins. */
public enum class StartRunRejection {
    /** The submitted prompt contained no non-whitespace characters. */
    BLANK_PROMPT,

    /** Another run already owns the conversation's active-generation slot. */
    CONVERSATION_BUSY,

    /** Live mode is selected, but its credential, model, or verification is stale. */
    LIVE_CONFIGURATION_NOT_READY,

    /** Durable storage rejected the attempt before provider work began. */
    STORAGE_UNAVAILABLE,

    /** The owning lifecycle has shut down and cannot accept more work. */
    LIFECYCLE_CLOSED,
}
