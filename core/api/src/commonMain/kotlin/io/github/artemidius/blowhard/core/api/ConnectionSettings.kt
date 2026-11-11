package io.github.artemidius.blowhard.core.api

import kotlinx.coroutines.flow.StateFlow

/** Shared application boundary for configuring an optional live generation provider. */
public interface ConnectionSettings {
    /** Current non-sensitive connection configuration. */
    public val state: StateFlow<ConnectionSettingsState>

    /** Refreshes whether a credential exists without exposing its value in [state]. */
    public suspend fun refreshCredentialStatus()

    /** Replaces the securely stored credential and invalidates prior connection evidence. */
    public suspend fun replaceCredential(secret: String): CredentialUpdateResult

    /** Deletes the securely stored credential and invalidates connection evidence. */
    public suspend fun deleteCredential(): CredentialUpdateResult

    /** Tests the credential with a non-generative model-catalog request. */
    public suspend fun testConnection(): ConnectionTestResult

    /** Selects one model from the latest verified compatible catalog. */
    public suspend fun selectModel(modelId: String): ModelSelectionResult

    /** Replaces the provider-neutral controls used by subsequently accepted attempts. */
    public suspend fun updateGenerationParameters(parameters: GenerationParameters)

    /** Records acknowledgement that live prompts are sent to the external provider. */
    public suspend fun acknowledgeLiveDataDisclosure()

    /** Selects demo or live generation without silently falling back between them. */
    public suspend fun selectMode(mode: GenerationMode): ModeSelectionResult
}

/** Immutable, secret-free state for provider settings and mode selection. */
public data class ConnectionSettingsState(
    public val mode: GenerationMode = GenerationMode.DEMO,
    public val credentialStatus: CredentialStatus = CredentialStatus.UNKNOWN,
    public val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_TESTED,
    public val models: List<SelectableModel> = emptyList(),
    public val selectedModelId: String? = null,
    public val generationParameters: GenerationParameters = GenerationParameters(),
    public val liveDataDisclosureAccepted: Boolean = false,
) {
    init {
        require(selectedModelId == null || models.any { it.id == selectedModelId }) {
            "Selected model must belong to the verified catalog"
        }
    }

    /** Whether all prerequisites for explicitly selecting live mode are present. */
    public val canUseLiveMode: Boolean
        get() = credentialStatus == CredentialStatus.CONFIGURED &&
            connectionStatus == ConnectionStatus.READY &&
            selectedModelId != null &&
            liveDataDisclosureAccepted
}

/** Provider-neutral model choice proven compatible by the latest connection test. */
public data class SelectableModel(
    public val id: String,
    public val contextWindowTokens: Long?,
    public val supportsVision: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Model identifier must not be blank" }
        require(contextWindowTokens == null || contextWindowTokens > 0) { "Context window must be positive" }
    }
}

/** Source used for new generation attempts. */
public enum class GenerationMode {
    /** Deterministic local fixture generation with no provider request. */
    DEMO,

    /** Explicitly selected external-provider generation. */
    LIVE,
}

/** Whether secure storage currently contains a usable credential record. */
public enum class CredentialStatus {
    /** Secure storage has not been inspected in this process. */
    UNKNOWN,

    /** Secure storage contains no credential. */
    MISSING,

    /** Secure storage contains a credential; its value is not exposed here. */
    CONFIGURED,

    /** Secure storage could not determine whether a credential exists. */
    UNAVAILABLE,
}

/** State of the non-generative connection check. */
public enum class ConnectionStatus {
    /** The current credential has not been checked. */
    NOT_TESTED,

    /** A catalog-based connection check is running. */
    TESTING,

    /** The latest check found at least one compatible model. */
    READY,

    /** The latest check failed or found no compatible model. */
    FAILED,
}

/** Outcome of replacing or deleting a secure credential. */
public enum class CredentialUpdateResult {
    /** The secure-store mutation completed. */
    SUCCESS,

    /** The supplied credential did not satisfy storage constraints. */
    REJECTED,

    /** Secure storage could not complete the mutation. */
    UNAVAILABLE,
}

/** Outcome of selecting a model from the verified catalog. */
public enum class ModelSelectionResult {
    /** The verified model selection changed. */
    UPDATED,

    /** The requested identifier is not in the verified compatible catalog. */
    NOT_AVAILABLE,
}

/** Outcome of an explicit demo/live mode selection. */
public enum class ModeSelectionResult {
    /** The requested generation mode was selected. */
    UPDATED,

    /** A verified credential, connection, and model are not all available. */
    LIVE_NOT_READY,

    /** The user has not acknowledged external processing of live prompt data. */
    DISCLOSURE_REQUIRED,
}

/** Result of one non-generative provider connection test. */
public sealed interface ConnectionTestResult {
    /** Compatible chat models verified for the current credential. */
    public data class Success(public val models: List<SelectableModel>) : ConnectionTestResult

    /** Sanitized reason the connection could not be verified. */
    public data class Failure(public val kind: ConnectionTestFailureKind) : ConnectionTestResult
}

/** Provider-neutral failure categories safe to present in connection settings. */
public enum class ConnectionTestFailureKind {
    /** No usable credential is configured. */
    CREDENTIAL_UNAVAILABLE,

    /** The provider rejected the configured credential. */
    UNAUTHORIZED,

    /** The credential does not authorize catalog access. */
    FORBIDDEN,

    /** The provider requires an active paid account. */
    PAYMENT_REQUIRED,

    /** The provider requested a lower request rate. */
    RATE_LIMITED,

    /** The provider, approved endpoint, or network is unavailable. */
    PROVIDER_UNAVAILABLE,

    /** The provider response could not be safely interpreted. */
    INVALID_RESPONSE,

    /** The verified catalog contains no explicitly compatible chat model. */
    NO_COMPATIBLE_MODELS,

    /** An unexpected failure was safely redacted. */
    UNEXPECTED,
}
