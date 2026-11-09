package io.github.artemidius.blowhard.adapters.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MacOsKeychainIntegrationTest {
    @Test
    fun `real keychain supports isolated credential lifecycle when explicitly enabled`() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Mac", true)) return
        if (System.getenv(INTEGRATION_ENVIRONMENT_VARIABLE) != "1") return

        val account = "integration-${ProcessHandle.current().pid()}"
        val gateway = MacOsKeychainGateway("io.github.artemidius.blowhard.tests", account)
        val secret = "synthetic-integration-value".encodeToByteArray()
        gateway.delete()

        try {
            assertEquals(KeychainMutation.Success, gateway.replace(secret))
            assertContentEquals(secret, assertIs<KeychainRead.Found>(gateway.read()).bytes)
        } finally {
            assertEquals(KeychainMutation.Success, gateway.delete())
        }
        assertEquals(KeychainRead.Missing, gateway.read())
    }
}

private const val INTEGRATION_ENVIRONMENT_VARIABLE = "BLOWHARD_KEYCHAIN_INTEGRATION"
