package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection boundary that remains inactive until a host installs native credential storage. */
public class FailClosedConnectionSettings : ConnectionSettings {
    private val mutableState = MutableStateFlow(
        ConnectionSettingsState(credentialStatus = CredentialStatus.UNAVAILABLE),
    )
    override val state: StateFlow<ConnectionSettingsState> = mutableState.asStateFlow()

    override suspend fun refreshCredentialStatus(): Unit = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.UNAVAILABLE

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.UNAVAILABLE

    override suspend fun testConnection(): ConnectionTestResult =
        ConnectionTestResult.Failure(ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE)

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.NOT_AVAILABLE

    override suspend fun updateGenerationParameters(parameters: GenerationParameters) {
        mutableState.value = mutableState.value.copy(generationParameters = parameters)
    }

    override suspend fun acknowledgeLiveDataDisclosure() {
        mutableState.value = mutableState.value.copy(liveDataDisclosureAccepted = true)
    }

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.LIVE_NOT_READY
}
