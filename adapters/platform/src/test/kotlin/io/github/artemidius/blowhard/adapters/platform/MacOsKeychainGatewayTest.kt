package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MacOsKeychainGatewayTest {
    @Test
    fun `read distinguishes missing locked and denied credentials`() {
        assertEquals(KeychainRead.Missing, gateway(readStatus = ITEM_NOT_FOUND).read())
        assertEquals(
            KeychainRead.Unavailable(CredentialAccessFailure.INTERACTION_REQUIRED),
            gateway(readStatus = INTERACTION_NOT_ALLOWED).read(),
        )
        assertEquals(
            KeychainRead.Unavailable(CredentialAccessFailure.ACCESS_DENIED),
            gateway(readStatus = AUTH_FAILED).read(),
        )
    }

    @Test
    fun `replace updates an existing credential`() {
        val native = FakeKeychainNative(updateStatuses = ArrayDeque(listOf(SUCCESS)))
        val secret = "replacement".encodeToByteArray()

        assertEquals(KeychainMutation.Success, gateway(native = native).replace(secret))

        assertEquals(1, native.updateCalls)
        assertEquals(0, native.addCalls)
        assertContentEquals(secret, native.capturedSecret)
    }

    @Test
    fun `replace resolves an add race by updating the winner`() {
        val native = FakeKeychainNative(
            updateStatuses = ArrayDeque(listOf(ITEM_NOT_FOUND, SUCCESS)),
            addStatus = DUPLICATE_ITEM,
        )

        assertEquals(KeychainMutation.Success, gateway(native = native).replace("replacement".encodeToByteArray()))
        assertEquals(2, native.updateCalls)
        assertEquals(1, native.addCalls)
    }

    @Test
    fun `delete is idempotent when the credential is absent`() {
        assertEquals(KeychainMutation.Success, gateway(deleteStatus = ITEM_NOT_FOUND).delete())
    }

    @Test
    fun `successful native read returns bytes`() {
        val result = assertIs<KeychainRead.Found>(gateway(bytes = "value".encodeToByteArray()).read())

        assertContentEquals("value".encodeToByteArray(), result.bytes)
    }

    private fun gateway(
        readStatus: Int = SUCCESS,
        bytes: ByteArray? = "value".encodeToByteArray(),
        deleteStatus: Int = SUCCESS,
        native: FakeKeychainNative =
            FakeKeychainNative(readStatus = readStatus, bytes = bytes, deleteStatus = deleteStatus),
    ): MacOsKeychainGateway = MacOsKeychainGateway("service", "account", native)
}

private class FakeKeychainNative(
    private val readStatus: Int = SUCCESS,
    private val bytes: ByteArray? = "value".encodeToByteArray(),
    private val updateStatuses: ArrayDeque<Int> = ArrayDeque(listOf(SUCCESS)),
    private val addStatus: Int = SUCCESS,
    private val deleteStatus: Int = SUCCESS,
) : KeychainNative {
    var updateCalls: Int = 0
    var addCalls: Int = 0
    var capturedSecret: ByteArray? = null

    override fun read(service: String, account: String): NativeReadResult = NativeReadResult(readStatus, bytes)

    override fun add(service: String, account: String, secret: ByteArray): Int {
        addCalls += 1
        capturedSecret = secret.copyOf()
        return addStatus
    }

    override fun update(service: String, account: String, secret: ByteArray): Int {
        updateCalls += 1
        capturedSecret = secret.copyOf()
        return updateStatuses.removeFirst()
    }

    override fun delete(service: String, account: String): Int = deleteStatus
}

private const val SUCCESS = 0
private const val AUTH_FAILED = -25_293
private const val DUPLICATE_ITEM = -25_299
private const val ITEM_NOT_FOUND = -25_300
private const val INTERACTION_NOT_ALLOWED = -25_308
