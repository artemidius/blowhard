package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreDeletionResult
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class DesktopDocumentProcessingStoreTest {
    @Test
    internal fun sourceBytesAndExtractionSurviveReopenWithDeterministicPageOrder() = withTemporaryDatabase { path ->
        val source = document()
        saveDocument(path, source)

        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            val found = assertIs<DocumentSourceLookupResult.Found>(store.readSource(source.id)).source
            val firstCopy = found.copyContent()
            assertContentEquals(SOURCE_CONTENT, firstCopy)
            firstCopy[0] = 0
            assertContentEquals(SOURCE_CONTENT, found.copyContent())
            assertEquals(DocumentExtractionSaveResult.SAVED, store.saveExtraction(extraction("first")))
        }

        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            val stored = assertIs<DocumentExtractionStoreLookupResult.Found>(
                store.findExtraction(source.id),
            ).extraction
            assertEquals("first", stored.processor)
            assertEquals(listOf(0, 1), stored.pages.map(ExtractedDocumentPage::index))
            assertEquals(listOf("# Page 1", "Page 2"), stored.pages.map(ExtractedDocumentPage::markdown))
        }
    }

    @Test
    internal fun replacementIsAtomicAndPreservesPriorExtractionWhenAPageInsertFails() = withTemporaryDatabase { path ->
        saveDocument(path, document())
        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            assertEquals(DocumentExtractionSaveResult.SAVED, store.saveExtraction(extraction("first")))
        }
        installSecondPageFailure(path)

        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            assertEquals(DocumentExtractionSaveResult.UNAVAILABLE, store.saveExtraction(extraction("second")))
            val retained = assertIs<DocumentExtractionStoreLookupResult.Found>(
                store.findExtraction(DOCUMENT_ID),
            ).extraction
            assertEquals("first", retained.processor)
            assertEquals(listOf("# Page 1", "Page 2"), retained.pages.map(ExtractedDocumentPage::markdown))
        }
    }

    @Test
    internal fun missingSourceAndClosedStorageReturnDistinctSafeOutcomes() = withTemporaryDatabase { path ->
        val store = DesktopDocumentProcessingStores.open(path)
        assertEquals(DocumentSourceLookupResult.NotFound, store.readSource(DOCUMENT_ID))
        assertEquals(DocumentExtractionSaveResult.SOURCE_NOT_FOUND, store.saveExtraction(extraction("fixture")))
        assertEquals(DocumentExtractionStoreLookupResult.SourceNotFound, store.findExtraction(DOCUMENT_ID))

        store.close()
        store.close()
        assertEquals(DocumentSourceLookupResult.Unavailable, store.readSource(DOCUMENT_ID))
        assertEquals(DocumentExtractionSaveResult.UNAVAILABLE, store.saveExtraction(extraction("fixture")))
        assertEquals(DocumentExtractionStoreLookupResult.Unavailable, store.findExtraction(DOCUMENT_ID))
    }

    @Test
    internal fun documentDeletionRemovesOwnedExtractionRowsExplicitly() = withTemporaryDatabase { path ->
        val source = document()
        saveDocument(path, source)
        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            assertEquals(DocumentExtractionStoreLookupResult.NotProcessed, store.findExtraction(source.id))
            assertEquals(DocumentExtractionSaveResult.SAVED, store.saveExtraction(extraction("fixture")))
        }

        DesktopDocumentStores.open(path).let { store ->
            try {
                assertEquals(DocumentStoreDeletionResult.DELETED, store.delete(source.id))
            } finally {
                store.close()
            }
        }
        assertEquals(0L, extractionRowCount(path, source.id))
        DesktopDocumentProcessingStores.open(path).useProcessingStore { store ->
            assertEquals(DocumentExtractionStoreLookupResult.SourceNotFound, store.findExtraction(source.id))
        }
    }

    private suspend fun saveDocument(path: String, source: ImportedDocument) {
        val store = DesktopDocumentStores.open(path)
        try {
            assertEquals(DocumentSaveResult.SAVED, store.save(source, SOURCE_CONTENT))
        } finally {
            store.close()
        }
    }

    private fun document(): ImportedDocument = ImportedDocument(
        id = DOCUMENT_ID,
        displayName = "meeting.pdf",
        sizeBytes = SOURCE_CONTENT.size.toLong(),
        importedAtEpochMilliseconds = 1,
        content = DocumentContentDescriptor.Pdf(pageCount = 2),
    )

    private fun extraction(processor: String): DocumentExtraction = DocumentExtraction(
        sourceDocumentId = DOCUMENT_ID,
        processor = processor,
        processedAtEpochMilliseconds = 2,
        pages = listOf(ExtractedDocumentPage(0, "# Page 1"), ExtractedDocumentPage(1, "Page 2")),
    )

    private fun installSecondPageFailure(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TRIGGER fail_second_extraction_page
                    BEFORE INSERT ON document_extraction_page
                    WHEN NEW.page_index = 1
                    BEGIN
                        SELECT RAISE(ABORT, 'injected page failure');
                    END;
                    """.trimIndent(),
                )
            }
        }
    }

    private fun extractionRowCount(path: String, id: DocumentId): Long =
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM document_extraction WHERE document_id = ?").use {
                it.setString(1, id.value)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }
        }

    private companion object {
        val DOCUMENT_ID = DocumentId("document-1")
        val SOURCE_CONTENT = "document".encodeToByteArray()
    }
}

private suspend fun <T> DocumentProcessingStore.useProcessingStore(block: suspend (DocumentProcessingStore) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
