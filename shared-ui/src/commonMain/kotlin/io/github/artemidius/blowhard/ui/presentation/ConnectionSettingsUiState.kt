package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConnectionSettingsState

/** Secret-free state rendered by the shared provider-settings screen. */
public data class ConnectionSettingsUiState(
    public val settings: ConnectionSettingsState,
    public val notice: String? = null,
)
