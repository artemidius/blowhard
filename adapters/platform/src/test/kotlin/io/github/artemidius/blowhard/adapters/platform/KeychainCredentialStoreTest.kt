package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KeychainCredentialStoreTest {
    @Test
    fun `read returns a safe credential and clears native bytes`() = runTest {
        val bytes = "synthetic-key".encodeToByteArray()
        val store =
            KeychainCredentialStore(
                FakeGateway(read = KeychainRead.Found(bytes)),
                StandardTestDispatcher(testScheduler),
            )

        val result = assertIs<CredentialReadResult.Available>(store.read())

        assertEquals("synthetic-key", result.secret)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `unsafe stored credential is unavailable without exposing content`() = runTest {
        val store = KeychainCredentialStore(
            FakeGateway(read = KeychainRead.Found("unsafe key".encodeToByteArray())),
            StandardTestDispatcher(testScheduler),
        )

        assertEquals(
            CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
            store.read(),
        )
    }

    @Test
    fun `replace rejects unsafe input before keychain access`() = runTest {
        val gateway = FakeGateway()
        val store = KeychainCredentialStore(gateway, StandardTestDispatcher(testScheduler))

        assertEquals(CredentialMutationResult.Rejected, store.replace("line\nbreak"))
        assertEquals(0, gateway.replaceCalls)
    }

    @Test
    fun `replace rejects a credential whose utf8 representation exceeds the byte limit`() = runTest {
        val gateway = FakeGateway()
        val store = KeychainCredentialStore(gateway, StandardTestDispatcher(testScheduler))

        assertEquals(CredentialMutationResult.Rejected, store.replace("é".repeat(2_049)))
        assertEquals(0, gateway.replaceCalls)
    }

    @Test
    fun `replace passes utf8 and clears its temporary bytes`() = runTest {
        val gateway = FakeGateway()
        val store = KeychainCredentialStore(gateway, StandardTestDispatcher(testScheduler))

        assertEquals(CredentialMutationResult.Success, store.replace("synthetic-key"))

        assertContentEquals("synthetic-key".encodeToByteArray(), gateway.capturedSecret)
        assertTrue(gateway.receivedSecret?.all { it == 0.toByte() } == true)
    }

    @Test
    fun `native exceptions become sanitized unavailable results`() = runTest {
        val store = KeychainCredentialStore(ThrowingGateway, StandardTestDispatcher(testScheduler))

        assertEquals(
            CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
            store.read(),
        )
        assertEquals(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
            store.replace("synthetic-key"),
        )
        assertEquals(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
            store.delete(),
        )
    }
}

private class FakeGateway(
    private val read: KeychainRead = KeychainRead.Missing,
    private val mutation: KeychainMutation = KeychainMutation.Success,
) : KeychainGateway {
    var replaceCalls: Int = 0
    var capturedSecret: ByteArray? = null
    var receivedSecret: ByteArray? = null

    override fun read(): KeychainRead = read

    override fun replace(secret: ByteArray): KeychainMutation {
        replaceCalls += 1
        receivedSecret = secret
        capturedSecret = secret.copyOf()
        return mutation
    }

    override fun delete(): KeychainMutation = mutation
}

private data object ThrowingGateway : KeychainGateway {
    override fun read(): KeychainRead = error("sensitive native detail")

    override fun replace(secret: ByteArray): KeychainMutation = error("sensitive native detail")

    override fun delete(): KeychainMutation = error("sensitive native detail")
}
