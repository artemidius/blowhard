package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.persistence.LocalDatabaseDeletionResult
import io.github.artemidius.blowhard.adapters.persistence.NativeConversationStores
import io.github.artemidius.blowhard.adapters.persistence.NativeLocalDatabaseFiles
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IosLocalDataEraserTest {
    @Test
    internal fun credentialAndDatabaseTargetsAreAttemptedIndependently() = runTest {
        val name = "host-reset-${NSUUID().UUIDString}.db"
        val credentials = RecordingCredentialStore(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
        )
        NativeConversationStores.open(name).useStore {
            it.save(StoredConversation(ConversationId("ios-reset"), "Private chat", 1, 2))
        }

        try {
            assertFalse(IosLocalDataEraser(credentials, name).erase())
            assertEquals(1, credentials.deleteCalls)
            assertEquals(LocalDatabaseDeletionResult.NOT_FOUND, NativeLocalDatabaseFiles.delete(name))
        } finally {
            NativeLocalDatabaseFiles.delete(name)
        }
    }

    @Test
    internal fun successfulErasureLeavesFreshDatabase() = runTest {
        val name = "host-reset-${NSUUID().UUIDString}.db"
        val credentials = RecordingCredentialStore(CredentialMutationResult.Success)
        NativeConversationStores.open(name).useStore {
            it.save(StoredConversation(ConversationId("ios-reset"), "Private chat", 1, 2))
        }

        try {
            assertTrue(IosLocalDataEraser(credentials, name).erase())
            NativeConversationStores.open(name).useStore { assertTrue(it.list().isEmpty()) }
            assertEquals(1, credentials.deleteCalls)
        } finally {
            NativeLocalDatabaseFiles.delete(name)
        }
    }
}

private class RecordingCredentialStore(private val deleteResult: CredentialMutationResult) : CredentialStore {
    var deleteCalls: Int = 0

    override suspend fun read(): CredentialReadResult = CredentialReadResult.Missing

    override suspend fun replace(secret: String): CredentialMutationResult = CredentialMutationResult.Rejected

    override suspend fun delete(): CredentialMutationResult {
        deleteCalls += 1
        return deleteResult
    }
}

private suspend fun <T> ConversationStore.useStore(block: suspend (ConversationStore) -> T): T = try {
    block(this)
} finally {
    close()
}
