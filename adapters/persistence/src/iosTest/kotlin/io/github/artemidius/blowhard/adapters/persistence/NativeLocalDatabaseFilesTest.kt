package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class NativeLocalDatabaseFilesTest {
    @Test
    internal fun deletionRemovesDurableDatabaseAndIsIdempotent() = runTest {
        val name = "local-data-${NSUUID().UUIDString}.db"
        NativeLocalDatabaseFiles.delete(name)

        try {
            val original = StoredConversation(ConversationId("ios-delete"), "Private chat", 1, 2)
            NativeConversationStores.open(name).useStore { store -> store.save(original) }

            assertEquals(LocalDatabaseDeletionResult.DELETED, NativeLocalDatabaseFiles.delete(name))
            NativeConversationStores.open(name).useStore { store -> assertTrue(store.list().isEmpty()) }
            assertEquals(LocalDatabaseDeletionResult.DELETED, NativeLocalDatabaseFiles.delete(name))
            assertEquals(LocalDatabaseDeletionResult.NOT_FOUND, NativeLocalDatabaseFiles.delete(name))
        } finally {
            NativeLocalDatabaseFiles.delete(name)
        }
    }
}

private suspend fun <T> ConversationStore.useStore(block: suspend (ConversationStore) -> T): T = try {
    block(this)
} finally {
    close()
}
