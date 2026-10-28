@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.artemidius.blowhard.adapters.mobile

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidKeystoreCredentialStoreTest {
    @Test
    fun `credential lifecycle uses encrypted records and clears temporary arrays`() = runTest {
        val cipher = FakeCredentialCipher()
        val records = FakeCredentialRecords()
        val store = store(cipher, records)

        assertEquals(CredentialReadResult.Missing, store.read())
        assertEquals(CredentialMutationResult.Success, store.replace("synthetic-key"))
        assertEquals("synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
        assertEquals(CredentialMutationResult.Success, store.delete())
        assertEquals(CredentialReadResult.Missing, store.read())

        assertTrue(cipher.receivedPlaintexts.all { bytes -> bytes.all { it == 0.toByte() } })
        assertTrue(records.receivedRecords.all { it.iv.allZero() && it.ciphertext.allZero() })
        assertEquals(1, cipher.deleteCalls)
    }

    @Test
    fun `unsafe input is rejected before encryption or persistence`() = runTest {
        val cipher = FakeCredentialCipher()
        val records = FakeCredentialRecords()
        val store = store(cipher, records)

        assertEquals(CredentialMutationResult.Rejected, store.replace("unsafe key"))
        assertEquals(0, cipher.encryptCalls)
        assertEquals(0, records.writeCalls)
    }

    @Test
    fun `corrupt record and cryptographic failures are sanitized`() = runTest {
        val corruptStore = store(FakeCredentialCipher(), FakeCredentialRecords(StoredCredentialRecord.Corrupt))
        val failingStore = store(ThrowingCredentialCipher, FakeCredentialRecords())

        assertEquals(unexpectedReadFailure(), corruptStore.read())
        assertEquals(unexpectedMutationFailure(), failingStore.replace("synthetic-key"))
    }

    @Test
    fun `replacement rotates an invalidated key once`() = runTest {
        val cipher = FakeCredentialCipher(invalidateFirstEncryption = true)
        val store = store(cipher, FakeCredentialRecords())

        assertEquals(CredentialMutationResult.Success, store.replace("synthetic-key"))

        assertEquals(2, cipher.encryptCalls)
        assertEquals(1, cipher.deleteCalls)
    }

    @Test
    fun `cancellation is never converted to an unavailable result`() = runTest {
        val store = store(CancellingCredentialCipher, FakeCredentialRecords())

        assertFailsWith<CancellationException> { store.replace("synthetic-key") }
    }

    private fun TestScope.store(cipher: CredentialCipher, records: CredentialRecordRepository) =
        AndroidKeystoreCredentialStore(cipher, records, StandardTestDispatcher(testScheduler))
}

private class FakeCredentialCipher(private val invalidateFirstEncryption: Boolean = false) : CredentialCipher {
    var encryptCalls = 0
    var deleteCalls = 0
    val receivedPlaintexts = mutableListOf<ByteArray>()

    override fun encrypt(plaintext: ByteArray): EncryptedCredential {
        encryptCalls += 1
        receivedPlaintexts += plaintext
        if (invalidateFirstEncryption && encryptCalls == 1) {
            throw CredentialKeyInvalidatedException(IllegalStateException("synthetic"))
        }
        return EncryptedCredential(
            iv = ByteArray(12) { 7 },
            ciphertext = plaintext + ByteArray(16) { 9 },
        )
    }

    override fun decrypt(encrypted: EncryptedCredential): ByteArray = encrypted.ciphertext.copyOf(
        newSize = encrypted.ciphertext.size - 16,
    )

    override fun deleteKey() {
        deleteCalls += 1
    }
}

private class FakeCredentialRecords(initial: StoredCredentialRecord = StoredCredentialRecord.Missing) :
    CredentialRecordRepository {
    private var stored: StoredCredentialRecord = initial
    var writeCalls = 0
    val receivedRecords = mutableListOf<EncryptedCredential>()

    override fun read(): StoredCredentialRecord = when (val current = stored) {
        is StoredCredentialRecord.Available -> StoredCredentialRecord.Available(current.encrypted.copy())
        else -> current
    }

    override fun write(encrypted: EncryptedCredential): Boolean {
        writeCalls += 1
        receivedRecords += encrypted
        stored = StoredCredentialRecord.Available(encrypted.copy())
        return true
    }

    override fun delete(): Boolean {
        (stored as? StoredCredentialRecord.Available)?.encrypted?.clear()
        stored = StoredCredentialRecord.Missing
        return true
    }
}

private object ThrowingCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedCredential = error("private failure")

    override fun decrypt(encrypted: EncryptedCredential): ByteArray = error("private failure")

    override fun deleteKey(): Unit = error("private failure")
}

private object CancellingCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedCredential = throw CancellationException("cancel")

    override fun decrypt(encrypted: EncryptedCredential): ByteArray = throw CancellationException("cancel")

    override fun deleteKey(): Unit = throw CancellationException("cancel")
}

private fun EncryptedCredential.copy(): EncryptedCredential = EncryptedCredential(iv.copyOf(), ciphertext.copyOf())

private fun ByteArray.allZero(): Boolean = all { it == 0.toByte() }

private fun unexpectedReadFailure() = CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED)

private fun unexpectedMutationFailure() = CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED)
