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

internal class IosKeychainCredentialStore(
    private val gateway: IosKeychainGateway,
    private val dispatcher: CoroutineDispatcher,
) : CredentialStore {
    private val operationMutex = Mutex()

    override suspend fun read(): CredentialReadResult = guardedRead {
        when (val result = gateway.read()) {
            IosKeychainRead.Missing -> CredentialReadResult.Missing
            is IosKeychainRead.Found -> decode(result.bytes)
            is IosKeychainRead.Unavailable -> CredentialReadResult.Unavailable(result.reason)
        }
    }

    override suspend fun replace(secret: String): CredentialMutationResult {
        val encoded = CredentialCodec.encode(secret) ?: return CredentialMutationResult.Rejected
        return try {
            guardedMutation { gateway.replace(encoded).toMutationResult() }
        } finally {
            encoded.fill(0)
        }
    }

    override suspend fun delete(): CredentialMutationResult = guardedMutation {
        gateway.delete().toMutationResult()
    }

    private fun decode(bytes: ByteArray): CredentialReadResult = try {
        CredentialCodec.decode(bytes)?.let(CredentialReadResult::Available) ?: unexpectedReadFailure()
    } finally {
        bytes.fill(0)
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

private fun IosKeychainMutation.toMutationResult(): CredentialMutationResult = when (this) {
    IosKeychainMutation.Success -> CredentialMutationResult.Success
    is IosKeychainMutation.Unavailable -> CredentialMutationResult.Unavailable(reason)
}

private fun unexpectedReadFailure(): CredentialReadResult.Unavailable =
    CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED)

private fun unexpectedMutationFailure(): CredentialMutationResult.Unavailable =
    CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED)
