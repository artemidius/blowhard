package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.persistence.LocalDatabaseDeletionResult
import io.github.artemidius.blowhard.adapters.persistence.NativeLocalDatabaseFiles
import io.github.artemidius.blowhard.composition.ApplicationLocalDataEraser
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialStore

/** Erases iOS credential and database targets after the active session has closed. */
internal class IosLocalDataEraser(private val credentialStore: CredentialStore, private val databaseName: String) :
    ApplicationLocalDataEraser {
    override suspend fun erase(): Boolean {
        val credentialErased = eraseCredential()
        val databaseErased = NativeLocalDatabaseFiles.delete(databaseName) != LocalDatabaseDeletionResult.FAILED
        return credentialErased && databaseErased
    }

    private suspend fun eraseCredential(): Boolean = try {
        credentialStore.delete() == CredentialMutationResult.Success
    } catch (_: Exception) {
        false
    }
}
