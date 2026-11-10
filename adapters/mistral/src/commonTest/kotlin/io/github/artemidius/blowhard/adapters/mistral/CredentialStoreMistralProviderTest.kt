package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialStoreMistralProviderTest {
    @Test
    fun `available credential is read just in time`() = runTest {
        val store = FakeCredentialStore(CredentialReadResult.Available("secret"))
        val provider = store.asMistralCredentialProvider()

        assertEquals("secret", provider.apiKey())
        assertEquals(1, store.readCalls)
    }

    @Test
    fun `missing and unavailable storage map to absent provider credential`() = runTest {
        val store = FakeCredentialStore(CredentialReadResult.Missing)
        val provider = store.asMistralCredentialProvider()

        assertNull(provider.apiKey())
        store.result = CredentialReadResult.Unavailable(CredentialAccessFailure.ACCESS_DENIED)
        assertNull(provider.apiKey())
    }
}

private class FakeCredentialStore(var result: CredentialReadResult) : CredentialStore {
    var readCalls: Int = 0

    override suspend fun read(): CredentialReadResult {
        readCalls += 1
        return result
    }

    override suspend fun replace(secret: String): CredentialMutationResult = CredentialMutationResult.Success

    override suspend fun delete(): CredentialMutationResult = CredentialMutationResult.Success
}
