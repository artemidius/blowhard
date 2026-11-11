package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.domain.CapabilitySupport
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import io.github.artemidius.blowhard.core.domain.ModelAvailability
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailure
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailureKind
import io.github.artemidius.blowhard.core.domain.ModelCatalogPort
import io.github.artemidius.blowhard.core.domain.ModelCatalogResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates secure credentials, provider verification, and generation-mode selection.
 *
 * The observable state never contains credential material. Provider models become selectable only
 * after the catalog explicitly reports both current availability and chat-completion support.
 */
public class DefaultConnectionSettings(
    private val credentials: CredentialStore,
    private val catalog: ModelCatalogPort,
    startInLiveMode: Boolean = false,
) : ConnectionSettings {
    private val operations = Mutex()
    private val mutableState = MutableStateFlow(
        ConnectionSettingsState(
            mode = if (startInLiveMode) GenerationMode.LIVE else GenerationMode.DEMO,
            liveDataDisclosureAccepted = startInLiveMode,
        ),
    )

    override val state: StateFlow<ConnectionSettingsState> = mutableState.asStateFlow()

    override suspend fun refreshCredentialStatus(): Unit = operations.withLock {
        val status = credentials.readStatus()
        mutableState.value = when (status) {
            CredentialStatus.CONFIGURED -> mutableState.value.copy(credentialStatus = status)
            else -> mutableState.value.resetConnectionEvidence(status)
        }
    }

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = operations.withLock {
        when (mutateCredential { credentials.replace(secret) }) {
            CredentialUpdateResult.SUCCESS -> {
                mutableState.value = mutableState.value.resetConnectionEvidence(CredentialStatus.CONFIGURED)
                CredentialUpdateResult.SUCCESS
            }

            CredentialUpdateResult.REJECTED -> CredentialUpdateResult.REJECTED

            CredentialUpdateResult.UNAVAILABLE -> CredentialUpdateResult.UNAVAILABLE
        }
    }

    override suspend fun deleteCredential(): CredentialUpdateResult = operations.withLock {
        when (mutateCredential(credentials::delete)) {
            CredentialUpdateResult.SUCCESS -> {
                mutableState.value = mutableState.value.resetConnectionEvidence(CredentialStatus.MISSING)
                CredentialUpdateResult.SUCCESS
            }

            CredentialUpdateResult.REJECTED -> CredentialUpdateResult.REJECTED

            CredentialUpdateResult.UNAVAILABLE -> CredentialUpdateResult.UNAVAILABLE
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = operations.withLock {
        if (mutableState.value.credentialStatus != CredentialStatus.CONFIGURED) {
            return@withLock ConnectionTestResult.Failure(
                ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE,
            )
        }

        val previous = mutableState.value
        mutableState.value = previous.copy(connectionStatus = ConnectionStatus.TESTING)
        val result = try {
            catalog.listModels()
        } catch (cancellation: CancellationException) {
            mutableState.value = previous
            throw cancellation
        } catch (_: Exception) {
            ModelCatalogResult.Failure(
                ModelCatalogFailure(ModelCatalogFailureKind.UNEXPECTED),
            )
        }

        applyCatalogResult(previous, result)
    }

    override suspend fun selectModel(modelId: String): ModelSelectionResult = operations.withLock {
        if (mutableState.value.models.none { it.id == modelId }) {
            return@withLock ModelSelectionResult.NOT_AVAILABLE
        }
        mutableState.value = mutableState.value.copy(selectedModelId = modelId)
        ModelSelectionResult.UPDATED
    }

    override suspend fun updateGenerationParameters(parameters: GenerationParameters): Unit = operations.withLock {
        mutableState.value = mutableState.value.copy(generationParameters = parameters)
    }

    override suspend fun acknowledgeLiveDataDisclosure(): Unit = operations.withLock {
        mutableState.value = mutableState.value.copy(liveDataDisclosureAccepted = true)
    }

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = operations.withLock {
        if (mode == GenerationMode.DEMO) {
            mutableState.value = mutableState.value.copy(mode = mode)
            return@withLock ModeSelectionResult.UPDATED
        }

        val current = mutableState.value
        val connectionReady = current.credentialStatus == CredentialStatus.CONFIGURED &&
            current.connectionStatus == ConnectionStatus.READY &&
            current.selectedModelId != null
        when {
            !connectionReady -> ModeSelectionResult.LIVE_NOT_READY

            !current.liveDataDisclosureAccepted -> ModeSelectionResult.DISCLOSURE_REQUIRED

            else -> {
                mutableState.value = current.copy(mode = GenerationMode.LIVE)
                ModeSelectionResult.UPDATED
            }
        }
    }

    private fun applyCatalogResult(
        previous: ConnectionSettingsState,
        result: ModelCatalogResult,
    ): ConnectionTestResult = when (result) {
        is ModelCatalogResult.Failure -> {
            mutableState.value = previous.copy(
                connectionStatus = ConnectionStatus.FAILED,
                models = emptyList(),
                selectedModelId = null,
            )
            ConnectionTestResult.Failure(result.failure.kind.toConnectionFailure())
        }

        is ModelCatalogResult.Success -> applyCompatibleModels(previous, result)
    }

    private fun applyCompatibleModels(
        previous: ConnectionSettingsState,
        result: ModelCatalogResult.Success,
    ): ConnectionTestResult {
        val compatible = result.models
            .asSequence()
            .filter { it.availability == ModelAvailability.AVAILABLE }
            .filter { it.capabilities.chatCompletion == CapabilitySupport.SUPPORTED }
            .map { SelectableModel(it.id, it.contextWindowTokens) }
            .distinctBy(SelectableModel::id)
            .sortedBy(SelectableModel::id)
            .toList()

        if (compatible.isEmpty()) {
            mutableState.value = previous.copy(
                connectionStatus = ConnectionStatus.FAILED,
                models = emptyList(),
                selectedModelId = null,
            )
            return ConnectionTestResult.Failure(ConnectionTestFailureKind.NO_COMPATIBLE_MODELS)
        }

        val priorSelection = previous.selectedModelId?.takeIf { selected ->
            compatible.any { it.id == selected }
        }
        val selectedModel = priorSelection ?: compatible.first().id
        mutableState.value = previous.copy(
            connectionStatus = ConnectionStatus.READY,
            models = compatible,
            selectedModelId = selectedModel,
        )
        return ConnectionTestResult.Success(compatible)
    }
}

private suspend fun mutateCredential(mutation: suspend () -> CredentialMutationResult): CredentialUpdateResult = try {
    when (mutation()) {
        CredentialMutationResult.Success -> CredentialUpdateResult.SUCCESS
        CredentialMutationResult.Rejected -> CredentialUpdateResult.REJECTED
        is CredentialMutationResult.Unavailable -> CredentialUpdateResult.UNAVAILABLE
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    CredentialUpdateResult.UNAVAILABLE
}

private fun ConnectionSettingsState.resetConnectionEvidence(
    credentialStatus: CredentialStatus,
): ConnectionSettingsState = ConnectionSettingsState(
    mode = mode,
    credentialStatus = credentialStatus,
    generationParameters = generationParameters,
    liveDataDisclosureAccepted = liveDataDisclosureAccepted,
)

private suspend fun CredentialStore.readStatus(): CredentialStatus = try {
    when (read()) {
        is CredentialReadResult.Available -> CredentialStatus.CONFIGURED
        CredentialReadResult.Missing -> CredentialStatus.MISSING
        is CredentialReadResult.Unavailable -> CredentialStatus.UNAVAILABLE
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    CredentialStatus.UNAVAILABLE
}

private fun ModelCatalogFailureKind.toConnectionFailure(): ConnectionTestFailureKind = when (this) {
    ModelCatalogFailureKind.CREDENTIAL_UNAVAILABLE -> ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE

    ModelCatalogFailureKind.UNAUTHORIZED -> ConnectionTestFailureKind.UNAUTHORIZED

    ModelCatalogFailureKind.FORBIDDEN -> ConnectionTestFailureKind.FORBIDDEN

    ModelCatalogFailureKind.PAYMENT_REQUIRED -> ConnectionTestFailureKind.PAYMENT_REQUIRED

    ModelCatalogFailureKind.RATE_LIMITED -> ConnectionTestFailureKind.RATE_LIMITED

    ModelCatalogFailureKind.UNAVAILABLE,
    ModelCatalogFailureKind.REDIRECT_REJECTED,
    -> ConnectionTestFailureKind.PROVIDER_UNAVAILABLE

    ModelCatalogFailureKind.INVALID_REQUEST,
    ModelCatalogFailureKind.NOT_FOUND,
    ModelCatalogFailureKind.INVALID_RESPONSE,
    -> ConnectionTestFailureKind.INVALID_RESPONSE

    ModelCatalogFailureKind.UNEXPECTED -> ConnectionTestFailureKind.UNEXPECTED
}
