package io.github.artemidius.blowhard.adapters.mobile

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidKeystoreCredentialStore(
    private val cipher: CredentialCipher,
    private val records: CredentialRecordRepository,
    private val dispatcher: CoroutineDispatcher,
) : CredentialStore {
    private val operationMutex = Mutex()

    override suspend fun read(): CredentialReadResult = guardedRead {
        when (val stored = records.read()) {
            StoredCredentialRecord.Missing -> CredentialReadResult.Missing
            StoredCredentialRecord.Corrupt -> unexpectedReadFailure()
            is StoredCredentialRecord.Available -> decrypt(stored.encrypted)
        }
    }

    override suspend fun replace(secret: String): CredentialMutationResult {
        val plaintext = CredentialCodec.encode(secret) ?: return CredentialMutationResult.Rejected
        return try {
            guardedMutation {
                val encrypted = encryptRecoveringInvalidatedKey(plaintext)
                try {
                    if (records.write(encrypted)) CredentialMutationResult.Success else unexpectedMutationFailure()
                } finally {
                    encrypted.clear()
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun delete(): CredentialMutationResult = guardedMutation {
        if (!records.delete()) return@guardedMutation unexpectedMutationFailure()
        cipher.deleteKey()
        CredentialMutationResult.Success
    }

    private fun decrypt(encrypted: EncryptedCredential): CredentialReadResult = try {
        val plaintext = cipher.decrypt(encrypted)
        try {
            CredentialCodec.decode(plaintext)?.let(CredentialReadResult::Available) ?: unexpectedReadFailure()
        } finally {
            plaintext.fill(0)
        }
    } finally {
        encrypted.clear()
    }

    private fun encryptRecoveringInvalidatedKey(plaintext: ByteArray): EncryptedCredential = try {
        cipher.encrypt(plaintext)
    } catch (_: CredentialKeyInvalidatedException) {
        cipher.deleteKey()
        cipher.encrypt(plaintext)
    }

    private suspend fun guardedRead(operation: () -> CredentialReadResult): CredentialReadResult = try {
        operationMutex.withLock { withContext(dispatcher) { operation() } }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        unexpectedReadFailure()
    }

    private suspend fun guardedMutation(operation: () -> CredentialMutationResult): CredentialMutationResult = try {
        operationMutex.withLock { withContext(dispatcher) { operation() } }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        unexpectedMutationFailure()
    }
}

private fun unexpectedReadFailure(): CredentialReadResult.Unavailable =
    CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED)

private fun unexpectedMutationFailure(): CredentialMutationResult.Unavailable =
    CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED)
