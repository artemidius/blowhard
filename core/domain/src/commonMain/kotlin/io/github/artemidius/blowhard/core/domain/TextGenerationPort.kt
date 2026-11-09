package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunId
import kotlinx.coroutines.flow.Flow

/**
 * Core-owned boundary implemented by text-generation providers.
 *
 * Collection performs one generation attempt. Cancelling the collecting coroutine must cancel the
 * provider operation; adapters must not create an unowned background request.
 */
public fun interface TextGenerationPort {
    /** Returns the ordered event stream for [request]. */
    public fun generate(request: GenerationRequest): Flow<GenerationEvent>
}

/** Provider-neutral input required to execute one generation attempt. */
public data class GenerationRequest(
    public val runId: RunId,
    public val conversationId: ConversationId,
    public val prompt: PromptText,
)

/** Provider-neutral event produced during generation. */
public sealed interface GenerationEvent {
    /** Adds [text] to the generated response. */
    public data class TextDelta(public val text: String) : GenerationEvent

    /** Marks successful completion of the provider stream. */
    public data object Completed : GenerationEvent

    /** Marks a safely classified provider failure. */
    public data class Failed(public val failure: GenerationFailure) : GenerationEvent
}

/** Failure information that is safe to pass from an adapter into application code. */
public data class GenerationFailure(public val kind: GenerationFailureKind, public val reference: String? = null)

/** Provider-neutral generation failure categories. */
public enum class GenerationFailureKind {
    /** Authentication or authorization failed. */
    UNAUTHORIZED,

    /** The provider rejected the request as invalid. */
    INVALID_REQUEST,

    /** The provider asked the client to reduce its request rate. */
    RATE_LIMITED,

    /** The provider or network was temporarily unavailable. */
    UNAVAILABLE,

    /** An unexpected, safely redacted failure occurred. */
    UNEXPECTED,
}
