package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.TokenUsage
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
    public val target: GenerationTarget = GenerationTarget.Demo,
    public val parameters: GenerationParameters = GenerationParameters(),
    public val image: GenerationImage? = null,
)

/** One bounded, application-authorized image copied into a provider request. */
public class GenerationImage private constructor(public val mediaType: GenerationImageMediaType, content: ByteArray) {
    private val ownedContent: ByteArray = content.copyOf()

    /** Returns a defensive copy of the selected image bytes. */
    public fun copyBytes(): ByteArray = ownedContent.copyOf()

    /** Number of bytes in the validated image. */
    public val sizeBytes: Int
        get() = ownedContent.size

    /** Creates validated image values without retaining caller-owned bytes. */
    public companion object {
        /** Copies a non-empty image that fits Mistral's documented per-image limit. */
        public fun from(mediaType: GenerationImageMediaType, content: ByteArray): GenerationImage? =
            content.takeIf { it.isNotEmpty() && it.size <= MAX_GENERATION_IMAGE_BYTES }
                ?.let { GenerationImage(mediaType, it) }
    }
}

/** Supported media types for one image question. */
public enum class GenerationImageMediaType(public val value: String) {
    PNG("image/png"),
    JPEG("image/jpeg"),
}

/** Mistral's documented maximum is 20 MB; the application uses a strict byte ceiling. */
public const val MAX_GENERATION_IMAGE_BYTES: Int = 20 * 1024 * 1024

/** Provider-neutral event produced during generation. */
public sealed interface GenerationEvent {
    /** Adds [text] to the generated response. */
    public data class TextDelta(public val text: String) : GenerationEvent

    /** Reports optional provider token counts without completing the attempt. */
    public data class Usage(public val value: TokenUsage) : GenerationEvent

    /** Marks successful completion of the provider stream. */
    public data object Completed : GenerationEvent

    /** Marks a safely classified provider failure. */
    public data class Failed(public val failure: GenerationFailure) : GenerationEvent
}

/** Failure information that is safe to pass from an adapter into application code. */
public data class GenerationFailure(
    public val kind: GenerationFailureKind,
    public val reference: String? = null,
    public val retryAdvice: GenerationRetryAdvice = GenerationRetryAdvice.DO_NOT_RETRY,
    public val retryAfterSeconds: Long? = null,
) {
    init {
        require(retryAfterSeconds == null || retryAfterSeconds >= 0) { "Retry delay must not be negative" }
        require(retryAfterSeconds == null || retryAdvice == GenerationRetryAdvice.RETRY_AFTER_DELAY) {
            "Retry delay requires delayed retry advice"
        }
    }
}

/** Conservative guidance for creating a distinct generation attempt after failure. */
public enum class GenerationRetryAdvice {
    /** Repeating the same request cannot resolve the failure safely. */
    DO_NOT_RETRY,

    /** A user may explicitly start a new attempt; the failed POST must never be replayed automatically. */
    EXPLICIT_NEW_ATTEMPT,

    /** A distinct attempt may be started after the provider-supplied delay. */
    RETRY_AFTER_DELAY,
}

/** Provider-neutral generation failure categories. */
public enum class GenerationFailureKind {
    /** No usable credential was available before network access. */
    CREDENTIAL_UNAVAILABLE,

    /** Authentication or authorization failed. */
    UNAUTHORIZED,

    /** The provider rejected the request as invalid. */
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
