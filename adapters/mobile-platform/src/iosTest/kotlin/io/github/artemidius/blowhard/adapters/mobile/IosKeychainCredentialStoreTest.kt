@file:Suppress("FunctionNaming")

package io.github.artemidius.blowhard.adapters.mobile

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosKeychainCredentialStoreTest {
    @Test
    fun `credential lifecycle clears temporary native bytes`() = runTest {
        val gateway = FakeIosKeychainGateway()
        val store = store(gateway)

        assertEquals(CredentialReadResult.Missing, store.read())
        assertEquals(CredentialMutationResult.Success, store.replace("synthetic-key"))
        assertEquals("synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
        assertEquals(CredentialMutationResult.Success, store.delete())
        assertEquals(CredentialReadResult.Missing, store.read())

        assertTrue(gateway.receivedSecrets.all { bytes -> bytes.all { it == 0.toByte() } })
        assertTrue(gateway.returnedReads.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    fun `unsafe input is rejected before keychain access`() = runTest {
        val gateway = FakeIosKeychainGateway()
        val store = store(gateway)

        assertEquals(CredentialMutationResult.Rejected, store.replace("unsafe key"))

        assertEquals(0, gateway.replaceCalls)
    }

    @Test
    fun `keychain and native failures are sanitized`() = runTest {
        val denied = store(
            FakeIosKeychainGateway(
                readResult = IosKeychainRead.Unavailable(CredentialAccessFailure.ACCESS_DENIED),
                mutationResult = IosKeychainMutation.Unavailable(CredentialAccessFailure.INTERACTION_REQUIRED),
            ),
        )
        val throwing = store(ThrowingIosKeychainGateway)

        assertEquals(
            CredentialReadResult.Unavailable(CredentialAccessFailure.ACCESS_DENIED),
            denied.read(),
        )
        assertEquals(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.INTERACTION_REQUIRED),
            denied.replace("synthetic-key"),
        )
        assertEquals(unexpectedReadFailure(), throwing.read())
        assertEquals(unexpectedMutationFailure(), throwing.replace("synthetic-key"))
    }

    @Test
    fun `cancellation is never converted to an unavailable result`() = runTest {
        val store = store(CancellingIosKeychainGateway)

        assertFailsWith<CancellationException> { store.replace("synthetic-key") }
    }

    @Test
    fun `native keychain supports isolated credential lifecycle`() = runTest {
        val suffix = Random.nextLong().toString().replace('-', 'n')
        val store = IosCredentialStores.create(
            service = "io.github.artemidius.blowhard.test.$suffix",
            account = "mistral-api-key",
        )
        try {
            assertEquals(CredentialMutationResult.Success, store.delete())
            assertEquals(CredentialReadResult.Missing, store.read())
            assertEquals(CredentialMutationResult.Success, store.replace("first-synthetic-key"))
            assertEquals("first-synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
            assertEquals(CredentialMutationResult.Success, store.replace("second-synthetic-key"))
            assertEquals("second-synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
            assertEquals(CredentialMutationResult.Success, store.delete())
            assertEquals(CredentialReadResult.Missing, store.read())
        } finally {
            store.delete()
        }
    }

    private fun TestScope.store(gateway: IosKeychainGateway) = IosKeychainCredentialStore(
        gateway,
        StandardTestDispatcher(testScheduler),
    )
}

private class FakeIosKeychainGateway(
    private val readResult: IosKeychainRead? = null,
    private val mutationResult: IosKeychainMutation = IosKeychainMutation.Success,
) : IosKeychainGateway {
    private var stored: ByteArray? = null
    var replaceCalls = 0
    val receivedSecrets = mutableListOf<ByteArray>()
    val returnedReads = mutableListOf<ByteArray>()

    override fun read(): IosKeychainRead = readResult ?: stored?.let { current ->
        val copy = current.copyOf()
        returnedReads += copy
        IosKeychainRead.Found(copy)
    } ?: IosKeychainRead.Missing

    override fun replace(secret: ByteArray): IosKeychainMutation {
        replaceCalls += 1
        receivedSecrets += secret
        stored?.fill(0)
        stored = secret.copyOf()
        return mutationResult
    }

    override fun delete(): IosKeychainMutation {
        stored?.fill(0)
        stored = null
        return mutationResult
    }
}

private object ThrowingIosKeychainGateway : IosKeychainGateway {
    override fun read(): IosKeychainRead = error("private failure")

    override fun replace(secret: ByteArray): IosKeychainMutation = error("private failure")

    override fun delete(): IosKeychainMutation = error("private failure")
}

private object CancellingIosKeychainGateway : IosKeychainGateway {
    override fun read(): IosKeychainRead = throw CancellationException("cancel")

    override fun replace(secret: ByteArray): IosKeychainMutation = throw CancellationException("cancel")

    override fun delete(): IosKeychainMutation = throw CancellationException("cancel")
}

private fun unexpectedReadFailure() = CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED)

private fun unexpectedMutationFailure() = CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED)
