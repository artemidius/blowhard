package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentStore
import io.github.artemidius.blowhard.core.domain.DocumentStoreDeletionResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreListResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreLookupResult
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class DesktopDocumentStoreTest {
    @Test
    internal fun metadataAndOwnedBytesSurviveReopen() = withTemporaryDatabase { path ->
        val content = "abc".encodeToByteArray()
        val original = pdf("document-1", importedAt = 10, sizeBytes = content.size.toLong())

        DesktopDocumentStores.open(path).useDocumentStore { store ->
            assertEquals(DocumentSaveResult.SAVED, store.save(original, content))
            content[0] = 'z'.code.toByte()
        }

        DesktopDocumentStores.open(path).useDocumentStore { store ->
            assertEquals(original, assertIs<DocumentStoreLookupResult.Found>(store.find(original.id)).document)
        }
        assertContentEquals("abc".encodeToByteArray(), readContent(path, original.id))
    }

    @Test
    internal fun saveRejectsIdentityConflictsAndListOrderIsDeterministic() = withTemporaryDatabase { path ->
        DesktopDocumentStores.open(path).useDocumentStore { store ->
            val older = pdf("older", importedAt = 1)
            val laterB = image("b", importedAt = 2)
            val laterA = image("a", importedAt = 2)

            assertEquals(DocumentSaveResult.SAVED, store.save(older, contentFor(older)))
            assertEquals(DocumentSaveResult.SAVED, store.save(laterB, contentFor(laterB)))
            assertEquals(DocumentSaveResult.SAVED, store.save(laterA, contentFor(laterA)))
            assertEquals(
                DocumentSaveResult.ID_CONFLICT,
                store.save(older.copy(displayName = "changed.pdf"), contentFor(older)),
            )
            assertEquals(
                DocumentSaveResult.UNAVAILABLE,
                store.save(pdf("mismatch", importedAt = 3, sizeBytes = 2), byteArrayOf(1)),
            )

            val documents = assertIs<DocumentStoreListResult.Available>(store.list()).documents
            assertEquals(listOf("a", "b", "older"), documents.map { it.id.value })
            assertEquals(
                "older.pdf",
                assertIs<DocumentStoreLookupResult.Found>(store.find(older.id)).document.displayName,
            )
            assertContentEquals(contentFor(older), readContent(path, older.id))
        }
    }

    @Test
    internal fun deleteRemovesMetadataAndContentAndClosedStoreFailsSafely() = withTemporaryDatabase { path ->
        val store = DesktopDocumentStores.open(path)
        val document = pdf("delete", importedAt = 1)
        assertEquals(DocumentSaveResult.SAVED, store.save(document, byteArrayOf(1)))
        assertEquals(DocumentStoreDeletionResult.DELETED, store.delete(document.id))
        assertEquals(DocumentStoreDeletionResult.NOT_FOUND, store.delete(document.id))
        assertEquals(DocumentStoreLookupResult.NotFound, store.find(document.id))
        assertEquals(0L, contentRowCount(path, document.id))

        store.close()
        store.close()
        assertEquals(DocumentSaveResult.UNAVAILABLE, store.save(document, byteArrayOf(1)))
        assertEquals(DocumentStoreListResult.Unavailable, store.list())
        assertEquals(DocumentStoreLookupResult.Unavailable, store.find(document.id))
        assertEquals(DocumentStoreDeletionResult.UNAVAILABLE, store.delete(document.id))
    }

    private fun pdf(id: String, importedAt: Long, sizeBytes: Long = 1): ImportedDocument = ImportedDocument(
        id = DocumentId(id),
        displayName = "$id.pdf",
        sizeBytes = sizeBytes,
        importedAtEpochMilliseconds = importedAt,
        content = DocumentContentDescriptor.Pdf(pageCount = 2),
    )

    private fun image(id: String, importedAt: Long): ImportedDocument = ImportedDocument(
        id = DocumentId(id),
        displayName = "$id.png",
        sizeBytes = 1,
        importedAtEpochMilliseconds = importedAt,
        content = DocumentContentDescriptor.Image(DocumentFormat.PNG, widthPixels = 10, heightPixels = 20),
    )

    private fun contentFor(document: ImportedDocument): ByteArray = byteArrayOf(document.id.value.first().code.toByte())

    private fun readContent(path: String, id: DocumentId): ByteArray =
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement("SELECT content FROM document WHERE id = ?").use { statement ->
                statement.setString(1, id.value)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getBytes(1)
                }
            }
        }

    private fun contentRowCount(path: String, id: DocumentId): Long =
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM document WHERE id = ?").use { statement ->
                statement.setString(1, id.value)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }
        }
}

private suspend fun <T> DocumentStore.useDocumentStore(block: suspend (DocumentStore) -> T): T = try {
    block(this)
} finally {
    close()
}
