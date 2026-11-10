package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Converts settings intents and sanitized application results into immutable presentation state.
 *
 * Credential values pass directly to [ConnectionSettings] and never enter [state]. Call [close]
 * when the owning client shuts down.
 */
public class ConnectionSettingsPresenter(
    private val connectionSettings: ConnectionSettings,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val actions = Channel<ConnectionSettingsAction>(Channel.UNLIMITED)
    private val notice = MutableStateFlow<String?>(null)

    /** Current settings state and sanitized user feedback. */
    public val state: StateFlow<ConnectionSettingsUiState> = combine(
        connectionSettings.state,
        notice,
        ::ConnectionSettingsUiState,
    ).stateIn(
        scope,
        SharingStarted.Eagerly,
        ConnectionSettingsUiState(connectionSettings.state.value),
    )

    init {
        scope.launch { processActions() }
        dispatch(ConnectionSettingsAction.RefreshRequested)
    }

    internal fun dispatch(action: ConnectionSettingsAction) {
        actions.trySend(action)
    }

    /** Stops presenter-owned work without closing application-owned settings resources. */
    public fun close() {
        actions.close()
        scope.cancel()
    }

    private suspend fun processActions() {
        for (action in actions) {
            try {
                handle(action)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                notice.value = "The settings operation could not be completed safely."
            }
        }
    }

    private suspend fun handle(action: ConnectionSettingsAction) {
        when (action) {
            is ConnectionSettingsAction.CredentialSubmitted -> replaceCredential(action.secret)

            ConnectionSettingsAction.CredentialDeleted -> deleteCredential()

            ConnectionSettingsAction.ConnectionTested -> testConnection()

            is ConnectionSettingsAction.ModelSelected -> selectModel(action.modelId)

            ConnectionSettingsAction.LiveDataDisclosureAccepted -> {
                connectionSettings.acknowledgeLiveDataDisclosure()
                notice.value = null
            }

            ConnectionSettingsAction.NoticeDismissed -> notice.value = null

            ConnectionSettingsAction.RefreshRequested -> connectionSettings.refreshCredentialStatus()
        }
    }

    private suspend fun replaceCredential(secret: String) {
        if (secret.isBlank()) {
            notice.value = "Enter an API key before saving."
            return
        }
        notice.value = when (connectionSettings.replaceCredential(secret)) {
            CredentialUpdateResult.SUCCESS -> "Credential saved. Test the connection to discover compatible models."
            CredentialUpdateResult.REJECTED -> "The credential does not satisfy secure-storage requirements."
            CredentialUpdateResult.UNAVAILABLE -> "Secure credential storage is unavailable on this device."
        }
    }

    private suspend fun deleteCredential() {
        notice.value = when (connectionSettings.deleteCredential()) {
            CredentialUpdateResult.SUCCESS -> "Credential deleted."
            CredentialUpdateResult.REJECTED -> "The credential could not be deleted."
            CredentialUpdateResult.UNAVAILABLE -> "Secure credential storage is unavailable on this device."
        }
    }

    private suspend fun testConnection() {
        notice.value = when (val result = connectionSettings.testConnection()) {
            is ConnectionTestResult.Success ->
                "Connection verified. ${result.models.size} compatible chat model(s) available."

            is ConnectionTestResult.Failure -> result.kind.safeMessage()
        }
    }

    private suspend fun selectModel(modelId: String) {
        notice.value = when (connectionSettings.selectModel(modelId)) {
            ModelSelectionResult.UPDATED -> null
            ModelSelectionResult.NOT_AVAILABLE -> "That model is not in the latest verified catalog."
        }
    }
}

private fun ConnectionTestFailureKind.safeMessage(): String = when (this) {
    ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE -> "Save a credential before testing the connection."
    ConnectionTestFailureKind.UNAUTHORIZED -> "The provider rejected the credential."
    ConnectionTestFailureKind.FORBIDDEN -> "The credential does not permit model discovery."
    ConnectionTestFailureKind.PAYMENT_REQUIRED -> "The provider requires an active paid account."
    ConnectionTestFailureKind.RATE_LIMITED -> "The provider rate limit was reached. Try again later."
    ConnectionTestFailureKind.PROVIDER_UNAVAILABLE -> "The provider or network is currently unavailable."
    ConnectionTestFailureKind.INVALID_RESPONSE -> "The provider returned an unsupported response."
    ConnectionTestFailureKind.NO_COMPATIBLE_MODELS -> "No explicitly compatible chat models were reported."
    ConnectionTestFailureKind.UNEXPECTED -> "The connection test failed safely."
}
