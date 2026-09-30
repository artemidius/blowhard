package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.domain.StoredConversation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DesktopConversationStoresTest {
    @Test
    internal fun metadataSurvivesCloseAndReopen() = withTemporaryDatabase { path ->
        val original = conversation("conversation-1", "Design café ☕", createdAt = 10, updatedAt = 20)

        DesktopConversationStores.open(path).useStore { store -> store.save(original) }
        DesktopConversationStores.open(path).useStore { store ->
            assertEquals(original, store.find(original.id))
        }
    }

    @Test
    internal fun savePreservesCreationTimeAndListUsesDeterministicRecentOrder() = withTemporaryDatabase { path ->
        val originalCreatedAt = 10L
        DesktopConversationStores.open(path).useStore { store ->
            store.save(conversation("b", "Older", createdAt = originalCreatedAt, updatedAt = 20))
            store.save(conversation("a", "Same time A", createdAt = 10, updatedAt = 30))
            store.save(conversation("c", "Same time C", createdAt = 10, updatedAt = 30))
            store.save(conversation("b", "Updated", createdAt = 19, updatedAt = 40))

            assertEquals(listOf("b", "a", "c"), store.list().map { it.id.value })
            assertEquals(originalCreatedAt, store.find(ConversationId("b"))?.createdAtEpochMilliseconds)
            assertEquals("Updated", store.find(ConversationId("b"))?.title)
        }
    }

    @Test
    internal fun deleteReportsRowExistenceAndClosedStoreFailsSafely() = withTemporaryDatabase { path ->
        val store = DesktopConversationStores.open(path)
        runTest {
            val id = ConversationId("conversation-1")
            store.save(conversation(id.value, "Delete me", createdAt = 1, updatedAt = 1))

            assertTrue(store.delete(id))
            assertFalse(store.delete(id))
            assertNull(store.find(id))
            store.close()
            store.close()
            assertFailsWith<IllegalStateException> { store.list() }
            assertFailsWith<IllegalStateException> {
                store.recoverInFlightAttempts(RunTimestamp(2))
            }
        }
    }

    private fun conversation(id: String, title: String, createdAt: Long, updatedAt: Long): StoredConversation =
        StoredFixtures.conversation(id, title, createdAt, updatedAt)
}
