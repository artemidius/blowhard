package io.github.artemidius.blowhard.core.domain

/** Core-owned boundary for discovering models available to the current provider account. */
public fun interface ModelCatalogPort {
    /** Returns a safely classified snapshot of the currently available model catalog. */
    public suspend fun listModels(): ModelCatalogResult
}

/** Result of one model-catalog request. */
public sealed interface ModelCatalogResult {
    /** Contains the provider models in stable identifier order. */
    public data class Success(public val models: List<AvailableModel>) : ModelCatalogResult

    /** Contains a safely classified failure without a provider response body. */
    public data class Failure(public val failure: ModelCatalogFailure) : ModelCatalogResult
}

/** Provider-neutral model information used for conservative feature selection. */
public data class AvailableModel(
    public val id: String,
    public val aliases: List<String>,
    public val contextWindowTokens: Long?,
    public val availability: ModelAvailability,
    public val capabilities: ModelCapabilities,
) {
    init {
        require(id.isNotBlank()) { "Model identifier must not be blank" }
        require(contextWindowTokens == null || contextWindowTokens > 0) { "Context window must be positive" }
    }
}

/** Whether a model can currently be selected. */
public enum class ModelAvailability {
    /** The provider explicitly reports the model as active. */
    AVAILABLE,

    /** The provider explicitly reports the model as archived. */
    ARCHIVED,

    /** The provider did not report availability. */
    UNKNOWN,
}

/** Explicit support state for each provider capability understood by the application. */
public enum class CapabilitySupport {
    /** The provider explicitly reports support. */
    SUPPORTED,

    /** The provider explicitly reports no support. */
    UNSUPPORTED,

    /** The provider omitted the capability. */
    UNKNOWN,
}

/** Capabilities currently understood by the shared application contract. */
public data class ModelCapabilities(
    public val chatCompletion: CapabilitySupport,
    public val fillInMiddle: CapabilitySupport,
    public val functionCalling: CapabilitySupport,
    public val fineTuning: CapabilitySupport,
    public val vision: CapabilitySupport,
    public val classification: CapabilitySupport,
)

/** Safe failure information for model discovery. */
public data class ModelCatalogFailure(
    public val kind: ModelCatalogFailureKind,
    public val retryAfterSeconds: Long? = null,
) {
    init {
        require(retryAfterSeconds == null || retryAfterSeconds >= 0) { "Retry delay must not be negative" }
    }
}

/** Provider-neutral model-catalog failure categories. */
public enum class ModelCatalogFailureKind {
    /** No usable credential was available to the adapter. */
    CREDENTIAL_UNAVAILABLE,

    /** The provider rejected the credential. */
    UNAUTHORIZED,

    /** The credential does not grant the required operation. */
    FORBIDDEN,

    /** The provider requires an active paid account. */
    PAYMENT_REQUIRED,

    /** The provider rejected the request shape. */
    INVALID_REQUEST,

    /** The requested endpoint was not found. */
    NOT_FOUND,

    /** The provider asked the client to reduce its request rate. */
    RATE_LIMITED,

    /** A redirect was rejected to keep credentials on the approved host. */
    REDIRECT_REJECTED,

    /** The provider or network was temporarily unavailable. */
    UNAVAILABLE,

    /** A successful response did not match the documented contract. */
    INVALID_RESPONSE,

    /** An unexpected, safely redacted failure occurred. */
    UNEXPECTED,
}
