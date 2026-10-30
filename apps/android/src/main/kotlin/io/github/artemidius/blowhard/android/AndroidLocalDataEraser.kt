package io.github.artemidius.blowhard.android

import android.content.Context
import io.github.artemidius.blowhard.adapters.persistence.AndroidLocalDatabaseFiles
import io.github.artemidius.blowhard.adapters.persistence.LocalDatabaseDeletionResult
import io.github.artemidius.blowhard.composition.ApplicationLocalDataEraser
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Erases Android credential and database targets after the active session has closed. */
internal class AndroidLocalDataEraser(
    context: Context,
    private val credentialStore: CredentialStore,
    private val databaseName: String,
) : ApplicationLocalDataEraser {
    private val applicationContext = context.applicationContext

    override suspend fun erase(): Boolean {
        val credentialErased = eraseCredential()
        val databaseErased = withContext(Dispatchers.IO) {
            AndroidLocalDatabaseFiles.delete(applicationContext, databaseName) != LocalDatabaseDeletionResult.FAILED
        }
        return credentialErased && databaseErased
    }

    private suspend fun eraseCredential(): Boolean = try {
        credentialStore.delete() == CredentialMutationResult.Success
    } catch (_: Exception) {
        false
    }
}
