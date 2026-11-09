package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopCredentialStoresTest {
    @Test
    fun `unsupported desktop hosts fail closed`() = runTest {
        val store = DesktopCredentialStores.createMistralApiKey("Linux")

        assertEquals(
            CredentialReadResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE),
            store.read(),
        )
        assertEquals(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE),
            store.replace("synthetic-key"),
        )
        assertEquals(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE),
            store.delete(),
        )
    }
}
