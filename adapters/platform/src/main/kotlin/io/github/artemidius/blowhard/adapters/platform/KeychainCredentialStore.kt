package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

internal class KeychainCredentialStore(
    private val gateway: KeychainGateway,
    private val dispatcher: CoroutineDispatcher,
) : CredentialStore {
    override suspend fun read(): CredentialReadResult = try {
        withContext(dispatcher) {
            when (val result = gateway.read()) {
                is KeychainRead.Found -> result.bytes.toCredentialResult()
                KeychainRead.Missing -> CredentialReadResult.Missing
                is KeychainRead.Unavailable -> CredentialReadResult.Unavailable(result.reason)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        unexpectedReadFailure()
    }

    override suspend fun replace(secret: String): CredentialMutationResult {
        val bytes = secret.toCredentialBytes() ?: return CredentialMutationResult.Rejected
        return try {
            withContext(dispatcher) { gateway.replace(bytes) }.toMutationResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            unexpectedMutationFailure()
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun delete(): CredentialMutationResult = try {
        withContext(dispatcher) { gateway.delete().toMutationResult() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        unexpectedMutationFailure()
    }
}

private fun ByteArray.toCredentialResult(): CredentialReadResult = try {
    if (isEmpty() || size > MAX_SECRET_BYTES) {
        unexpectedReadFailure()
    } else {
        val secret = String(this, StandardCharsets.UTF_8)
        if (!secret.isSafeCredential() || !secret.hasSameUtf8BytesAs(this)) {
            unexpectedReadFailure()
        } else {
            CredentialReadResult.Available(secret)
        }
    }
} finally {
    fill(0)
}

private fun String.isSafeCredential(): Boolean =
    isNotBlank() && length <= MAX_SECRET_BYTES && none { it.isWhitespace() || it.isISOControl() }

private fun String.toCredentialBytes(): ByteArray? {
    if (!isSafeCredential()) return null
    val bytes = toByteArray(StandardCharsets.UTF_8)
    return if (bytes.size <= MAX_SECRET_BYTES) bytes else null.also { bytes.fill(0) }
}

private fun String.hasSameUtf8BytesAs(expected: ByteArray): Boolean {
    val encoded = toByteArray(StandardCharsets.UTF_8)
    return try {
        encoded.contentEquals(expected)
    } finally {
        encoded.fill(0)
    }
}

private fun KeychainMutation.toMutationResult(): CredentialMutationResult = when (this) {
    KeychainMutation.Success -> CredentialMutationResult.Success
    is KeychainMutation.Unavailable -> CredentialMutationResult.Unavailable(reason)
}

private fun unexpectedReadFailure(): CredentialReadResult.Unavailable =
    CredentialReadResult.Unavailable(io.github.artemidius.blowhard.core.domain.CredentialAccessFailure.UNEXPECTED)

private fun unexpectedMutationFailure(): CredentialMutationResult.Unavailable =
    CredentialMutationResult.Unavailable(io.github.artemidius.blowhard.core.domain.CredentialAccessFailure.UNEXPECTED)

private const val MAX_SECRET_BYTES = 4_096
