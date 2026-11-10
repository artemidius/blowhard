package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.ConversationId
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class DesktopConversationMigrationTest {
    @Test
    internal fun versionOneDatabaseMigratesWithoutLosingConversationMetadata() {
        val path = Files.createTempFile("blowhard-migration-", ".db")
        try {
            copyVersionOneFixture(path.toString())
            insertVersionOneConversation(path.toString())

            runTest {
                DesktopConversationStores.open(path.toString()).useStore { store ->
                    val conversation = assertNotNull(store.find(ConversationId("legacy")))
                    assertEquals("Legacy conversation", conversation.title)
                    assertEquals(emptyList(), store.loadTranscript(conversation.id)?.messages)
                }
            }
        } finally {
            path.deleteIfExists()
        }
    }

    private fun copyVersionOneFixture(destination: String) {
        val fixture = requireNotNull(javaClass.classLoader.getResourceAsStream("1.db"))
        fixture.use { input ->
            Files.copy(input, java.nio.file.Path.of(destination), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun insertVersionOneConversation(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO conversation VALUES ('legacy', 'Legacy conversation', 10, 20)",
                )
                statement.execute("PRAGMA user_version = 1")
            }
        }
    }
}
