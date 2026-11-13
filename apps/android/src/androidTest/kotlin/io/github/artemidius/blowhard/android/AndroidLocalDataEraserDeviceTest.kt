package io.github.artemidius.blowhard.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artemidius.blowhard.adapters.persistence.AndroidConversationStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidLocalDatabaseFiles
import io.github.artemidius.blowhard.adapters.persistence.LocalDatabaseDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Device integration coverage for the Android host's complete local-data boundary. */
@RunWith(AndroidJUnit4::class)
public class AndroidLocalDataEraserDeviceTest {
    /** Proves credential failure cannot skip independent database erasure. */
    @Test
    public fun credentialAndDatabaseTargetsAreAttemptedIndependently(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "host-reset-${UUID.randomUUID()}.db"
        val credentials = RecordingCredentialStore(
            CredentialMutationResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
        )
        val stored = StoredConversation(ConversationId("android-reset"), "Private chat", 1, 2)
        AndroidConversationStores.open(context, name).useStore { it.save(stored) }

        try {
            val erased = AndroidLocalDataEraser(context, credentials, name).erase()

            assertFalse(erased)
            assertEquals(1, credentials.deleteCalls)
            assertEquals(LocalDatabaseDeletionResult.NOT_FOUND, AndroidLocalDatabaseFiles.delete(context, name))
        } finally {
            AndroidLocalDatabaseFiles.delete(context, name)
        }
    }

    /** Proves successful reset removes both targets and leaves a fresh empty database. */
    @Test
    public fun successfulErasureLeavesFreshDatabase(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "host-reset-${UUID.randomUUID()}.db"
        val credentials = RecordingCredentialStore(CredentialMutationResult.Success)
        AndroidConversationStores.open(context, name).useStore {
            it.save(StoredConversation(ConversationId("android-reset"), "Private chat", 1, 2))
        }

        try {
            assertTrue(AndroidLocalDataEraser(context, credentials, name).erase())
            AndroidConversationStores.open(context, name).useStore { assertTrue(it.list().isEmpty()) }
            assertEquals(1, credentials.deleteCalls)
        } finally {
            AndroidLocalDatabaseFiles.delete(context, name)
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
