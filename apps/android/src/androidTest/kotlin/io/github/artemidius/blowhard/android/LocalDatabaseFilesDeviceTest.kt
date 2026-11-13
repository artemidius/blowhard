package io.github.artemidius.blowhard.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artemidius.blowhard.adapters.persistence.AndroidConversationStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidLocalDatabaseFiles
import io.github.artemidius.blowhard.adapters.persistence.LocalDatabaseDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.StoredConversation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Device integration coverage for complete application-database erasure. */
@RunWith(AndroidJUnit4::class)
public class LocalDatabaseFilesDeviceTest {
    /** Proves durable content disappears and repeated erasure reports stable outcomes. */
    @Test
    public fun deletionRemovesDurableDatabaseAndIsIdempotent(): Unit = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "local-data-${UUID.randomUUID()}.db"
        AndroidLocalDatabaseFiles.delete(context, name)

        try {
            val original = StoredConversation(ConversationId("android-delete"), "Private chat", 1, 2)
            AndroidConversationStores.open(context, name).useStore { store -> store.save(original) }

            assertEquals(LocalDatabaseDeletionResult.DELETED, AndroidLocalDatabaseFiles.delete(context, name))
            AndroidConversationStores.open(context, name).useStore { store -> assertTrue(store.list().isEmpty()) }
            assertEquals(LocalDatabaseDeletionResult.DELETED, AndroidLocalDatabaseFiles.delete(context, name))
            assertEquals(LocalDatabaseDeletionResult.NOT_FOUND, AndroidLocalDatabaseFiles.delete(context, name))
        } finally {
            AndroidLocalDatabaseFiles.delete(context, name)
        }
    }
}

private suspend fun <T> io.github.artemidius.blowhard.core.domain.ConversationStore.useStore(
    block: suspend (io.github.artemidius.blowhard.core.domain.ConversationStore) -> T,
): T = try {
    block(this)
} finally {
    close()
}
