package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureDocumentOcrPort
import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.core.application.DocumentIdFactory
import io.github.artemidius.blowhard.core.application.DocumentImportClock
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class DesktopDocumentSessionTest {
    @Test
    internal fun `real composition imports reopens and deletes a synthetic document`() =
        withTemporaryConfiguration { directory ->
            val id = DocumentId("stable-document-id")
            val first = DesktopDocumentSession.open(
                configurationDirectory = directory,
                idFactory = DocumentIdFactory { id },
                clock = DocumentImportClock { IMPORT_TIME },
                processor = FixtureDocumentOcrPort(),
            )
            assertTrue(first.available)
            val imported = runBlocking { first.library.import(ByteArrayDocumentSource("fixture.pdf", PDF)) }
            assertEquals(id, assertIs<DocumentImportResult.Imported>(imported).document.id)
            val processed = runBlocking { first.ocr.process(id) }
            assertEquals(1, assertIs<DocumentProcessingResult.Processed>(processed).extraction.pages.size)
            first.close()
            first.close()

            val reopened = DesktopDocumentSession.open(directory)
            assertTrue(reopened.available)
            runBlocking {
                val documents = assertIs<DocumentListResult.Available>(reopened.library.list()).documents
                assertEquals(listOf(id), documents.map { it.id })
                assertEquals(IMPORT_TIME, documents.single().importedAtEpochMilliseconds)
                val extraction = assertIs<DocumentExtractionLookupResult.Found>(reopened.ocr.find(id)).extraction
                assertEquals("fixture-ocr-v1", extraction.processor)
                assertEquals(DocumentDeletionResult.DELETED, reopened.library.delete(id))
            }
            reopened.close()

            val afterDeletion = DesktopDocumentSession.open(directory)
            runBlocking {
                assertTrue(assertIs<DocumentListResult.Available>(afterDeletion.library.list()).documents.isEmpty())
            }
            afterDeletion.close()
        }

    @Test
    internal fun `storage startup failure exposes only typed unavailable results`() =
        withTemporaryConfiguration { directory ->
            val session = DesktopDocumentSession.open(directory, openStore = { error("private path detail") })

            assertFalse(session.available)
            runBlocking {
                assertEquals(DocumentListResult.StorageUnavailable, session.library.list())
                assertEquals(
                    DocumentDeletionResult.STORAGE_UNAVAILABLE,
                    session.library.delete(DocumentId("missing")),
                )
            }
            session.close()
        }

    private fun withTemporaryConfiguration(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("blowhard-desktop-documents-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private class ByteArrayDocumentSource(override val displayName: String, private val bytes: ByteArray) :
        DocumentImportSource {
        override val declaredSizeBytes: Long = bytes.size.toLong()

        override suspend fun read(maximumBytes: Long): DocumentReadResult =
            if (bytes.size <= maximumBytes) DocumentReadResult.Content(bytes) else DocumentReadResult.TooLarge
    }

    private companion object {
        const val IMPORT_TIME = 42L
        val PDF = "%PDF-1.7\n1 0 obj << /Type /Page >> endobj\n%%EOF\n".encodeToByteArray()
    }
}
