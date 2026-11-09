package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ConnectionSettingsTest {
    @Test
    fun `selected model must belong to the verified catalog`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionSettingsState(selectedModelId = "unverified")
        }
    }

    @Test
    fun `live mode remains unavailable without every explicit prerequisite`() {
        val state = ConnectionSettingsState(
            credentialStatus = CredentialStatus.CONFIGURED,
            connectionStatus = ConnectionStatus.READY,
            models = listOf(SelectableModel("chat-model", 32_000)),
            selectedModelId = "chat-model",
        )

        assertFalse(state.canUseLiveMode)
    }
}
