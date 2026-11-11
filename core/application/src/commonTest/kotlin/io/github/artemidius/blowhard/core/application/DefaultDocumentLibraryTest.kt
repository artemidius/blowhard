package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult
import io.github.artemidius.blowhard.core.domain.DocumentInspector
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentStore
import io.github.artemidius.blowhard.core.domain.DocumentStoreDeletionResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreListResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DefaultDocumentLibraryTest {
    @Test
    fun `imports inspected content without exposing a platform path`() = runTest {
        val store = RecordingDocumentStore()
        val sourceBytes = byteArrayOf(1, 2, 3)
        val source = FakeSource("notes.pdf", sourceBytes.size.toLong(), DocumentReadResult.Content(sourceBytes))
        val library = library(store = store, descriptor = DocumentContentDescriptor.Pdf(3))

        val result = assertIs<DocumentImportResult.Imported>(library.import(source))

        assertEquals(DocumentId("document-1"), result.document.id)
        assertEquals("notes.pdf", result.document.displayName)
        assertEquals(3L, result.document.sizeBytes)
        assertEquals(123L, result.document.importedAtEpochMilliseconds)
        assertEquals(DocumentContentDescriptor.Pdf(3), result.document.content)
        assertEquals(TEST_POLICY.maximumBytes, source.maximumBytes)
        assertContentEquals(sourceBytes, store.savedContent)
        sourceBytes[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), store.savedContent)
    }

    @Test
    fun `rejects invalid metadata before reading selected content`() = runTest {
        val blank = FakeSource(" ", 1, DocumentReadResult.Content(byteArrayOf(1)))
        val oversized =
            FakeSource("large.pdf", TEST_POLICY.maximumBytes + 1, DocumentReadResult.Content(byteArrayOf(1)))
        val library = library()

        assertRejected(DocumentImportRejection.INVALID_NAME, library.import(blank))
        assertRejected(DocumentImportRejection.TOO_LARGE, library.import(oversized))
        assertEquals(0, blank.readCount)
        assertEquals(0, oversized.readCount)
    }

    @Test
    fun `maps bounded source failures without invoking inspection`() = runTest {
        var inspectionCount = 0
        val library = library(
            inspector = DocumentInspector {
                inspectionCount += 1
                DocumentInspectionResult.Inspected(DocumentContentDescriptor.Pdf(1))
            },
        )

        assertRejected(
            DocumentImportRejection.EMPTY_CONTENT,
            library.import(source(DocumentReadResult.Content(byteArrayOf()))),
        )
        assertRejected(DocumentImportRejection.TOO_LARGE, library.import(source(DocumentReadResult.TooLarge)))
        assertRejected(
            DocumentImportRejection.SOURCE_UNAVAILABLE,
            library.import(source(DocumentReadResult.Unavailable)),
        )
        assertRejected(
            DocumentImportRejection.SOURCE_UNAVAILABLE,
            library.import(source(error = IllegalStateException("private source detail"))),
        )
        assertEquals(0, inspectionCount)
    }

    @Test
    fun `enforces format page and pixel policy before storage`() = runTest {
        assertRejected(
            DocumentImportRejection.UNSUPPORTED_FORMAT,
            library(inspection = DocumentInspectionResult.UnsupportedFormat).import(validSource()),
        )
        assertRejected(
            DocumentImportRejection.INVALID_CONTENT,
            library(inspection = DocumentInspectionResult.InvalidContent).import(validSource()),
        )
        assertRejected(
            DocumentImportRejection.PAGE_LIMIT_EXCEEDED,
            library(descriptor = DocumentContentDescriptor.Pdf(TEST_POLICY.maximumPdfPages + 1)).import(validSource()),
        )
        assertRejected(
            DocumentImportRejection.PIXEL_LIMIT_EXCEEDED,
            library(
                descriptor = DocumentContentDescriptor.Image(DocumentFormat.PNG, 101, 100),
            ).import(validSource()),
        )
    }

    @Test
    fun `maps storage outcomes and lifecycle operations`() = runTest {
        val document = document()
        val store = RecordingDocumentStore(
            saveResult = DocumentSaveResult.UNAVAILABLE,
            listResult = DocumentStoreListResult.Available(listOf(document)),
            lookupResult = DocumentStoreLookupResult.Found(document),
            deletionResult = DocumentStoreDeletionResult.DELETED,
        )
        val library = library(store = store)

        assertRejected(DocumentImportRejection.STORAGE_UNAVAILABLE, library.import(validSource()))
        assertEquals(DocumentListResult.Available(listOf(document)), library.list())
        assertEquals(DocumentLookupResult.Found(document), library.find(document.id))
        assertEquals(DocumentDeletionResult.DELETED, library.delete(document.id))
    }

    @Test
    fun `fails closed on storage exceptions and preserves cancellation`() = runTest {
        val failingStore = RecordingDocumentStore(error = IllegalStateException("private database detail"))
        val library = library(store = failingStore)

        assertRejected(DocumentImportRejection.STORAGE_UNAVAILABLE, library.import(validSource()))
        assertEquals(DocumentListResult.StorageUnavailable, library.list())
        assertEquals(DocumentLookupResult.StorageUnavailable, library.find(DocumentId("missing")))
        assertEquals(DocumentDeletionResult.STORAGE_UNAVAILABLE, library.delete(DocumentId("missing")))

        val cancellation = CancellationException("cancel import")
        assertFailsWith<CancellationException> {
            library().import(source(error = cancellation))
        }
    }

    @Test
    fun `fails closed when import infrastructure cannot create durable metadata`() = runTest {
        val failingIdFactory = DocumentIdFactory { error("private identity detail") }
        val failingClock = DocumentImportClock { error("private clock detail") }

        assertRejected(
            DocumentImportRejection.STORAGE_UNAVAILABLE,
            library(idFactory = failingIdFactory).import(validSource()),
        )
        assertRejected(
            DocumentImportRejection.STORAGE_UNAVAILABLE,
            library(clock = failingClock).import(validSource()),
        )
    }

    private fun library(
        store: DocumentStore = RecordingDocumentStore(),
        descriptor: DocumentContentDescriptor = DocumentContentDescriptor.Pdf(1),
        inspection: DocumentInspectionResult = DocumentInspectionResult.Inspected(descriptor),
        inspector: DocumentInspector = DocumentInspector { inspection },
        idFactory: DocumentIdFactory = DocumentIdFactory { DocumentId("document-1") },
        clock: DocumentImportClock = DocumentImportClock { 123L },
    ): DefaultDocumentLibrary = DefaultDocumentLibrary(
        store = store,
        inspector = inspector,
        idFactory = idFactory,
        clock = clock,
        policy = TEST_POLICY,
    )

    private companion object {
        val TEST_POLICY = DocumentImportPolicy(maximumBytes = 8, maximumPdfPages = 4, maximumImagePixels = 10_000)
    }
}

private class FakeSource(
    override val displayName: String,
    override val declaredSizeBytes: Long?,
    private val result: DocumentReadResult = DocumentReadResult.Content(byteArrayOf(1)),
    private val error: Exception? = null,
) : DocumentImportSource {
    var readCount = 0
    var maximumBytes: Long? = null

    override suspend fun read(maximumBytes: Long): DocumentReadResult {
        readCount += 1
        this.maximumBytes = maximumBytes
        error?.let { throw it }
        return result
    }
}

private class RecordingDocumentStore(
    private val saveResult: DocumentSaveResult = DocumentSaveResult.SAVED,
    private val listResult: DocumentStoreListResult = DocumentStoreListResult.Available(emptyList()),
    private val lookupResult: DocumentStoreLookupResult = DocumentStoreLookupResult.NotFound,
    private val deletionResult: DocumentStoreDeletionResult = DocumentStoreDeletionResult.NOT_FOUND,
    private val error: Exception? = null,
) : DocumentStore {
    var savedContent: ByteArray? = null

    override suspend fun save(document: ImportedDocument, content: ByteArray): DocumentSaveResult {
        error?.let { throw it }
        savedContent = content.copyOf()
        return saveResult
    }

    override suspend fun list(): DocumentStoreListResult {
        error?.let { throw it }
        return listResult
    }

    override suspend fun find(id: DocumentId): DocumentStoreLookupResult {
        error?.let { throw it }
        return lookupResult
    }

    override suspend fun delete(id: DocumentId): DocumentStoreDeletionResult {
        error?.let { throw it }
        return deletionResult
    }
}

private fun source(result: DocumentReadResult? = null, error: Exception? = null): DocumentImportSource =
    FakeSource("notes.pdf", null, result ?: DocumentReadResult.Content(byteArrayOf(1)), error)

private fun validSource(): DocumentImportSource = source(DocumentReadResult.Content(byteArrayOf(1, 2)))

private fun document(): ImportedDocument = ImportedDocument(
    id = DocumentId("existing"),
    displayName = "existing.pdf",
    sizeBytes = 2,
    importedAtEpochMilliseconds = 1,
    content = DocumentContentDescriptor.Pdf(1),
)

private fun assertRejected(expected: DocumentImportRejection, result: DocumentImportResult) {
    assertEquals(expected, assertIs<DocumentImportResult.Rejected>(result).reason)
}
