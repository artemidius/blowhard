package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.GenerationMode

internal sealed interface ConnectionSettingsAction {
    class CredentialSubmitted(val secret: String) : ConnectionSettingsAction {
        override fun toString(): String = "CredentialSubmitted(secret=<redacted>)"
    }

    data object CredentialDeleted : ConnectionSettingsAction

    data object ConnectionTested : ConnectionSettingsAction

    data class ModelSelected(val modelId: String) : ConnectionSettingsAction

    data class MaxOutputTokensSelected(val value: Int?) : ConnectionSettingsAction

    data class TemperatureSelected(val value: Double?) : ConnectionSettingsAction

    data class ModeSelected(val mode: GenerationMode) : ConnectionSettingsAction

    data object LiveDataDisclosureAccepted : ConnectionSettingsAction

    data object NoticeDismissed : ConnectionSettingsAction

    data object RefreshRequested : ConnectionSettingsAction
}
