package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentProcessingRejection
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrFailureKind
import io.github.artemidius.blowhard.core.domain.DocumentOcrPage
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.StoredDocumentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class DefaultDocumentOcrTest {
    @Test
    internal fun `valid ordered pages including an empty page are stored with source provenance`() = runTest {
        val store = FakeProcessingStore(sourceResult = foundSource())
        var observedContent: ByteArray? = null
        val service = service(
            store,
            DocumentOcrPort { request ->
                observedContent = request.copyContent()
                DocumentOcrPortResult.Processed(
                    processor = "fixture-ocr-v1",
                    pages = listOf(DocumentOcrPage(0, "# Agenda"), DocumentOcrPage(1, "")),
                )
            },
        )

        val extraction = assertIs<DocumentProcessingResult.Processed>(service.process(DOCUMENT_ID)).extraction

        assertEquals(DOCUMENT_ID, extraction.sourceDocumentId)
        assertEquals(PROCESSED_AT, extraction.processedAtEpochMilliseconds)
        assertEquals(listOf("# Agenda", ""), extraction.pages.map { it.markdown })
        assertEquals(extraction, store.savedExtraction)
        assertEquals(SOURCE_BYTES.toList(), observedContent?.toList())
        observedContent!![0] = 0
        assertEquals(SOURCE_BYTES.toList(), store.source.copyContent().toList())
    }

    @Test
    internal fun `source and storage outcomes remain distinct`() = runTest {
        assertRejected(
            DocumentProcessingRejection.DOCUMENT_NOT_FOUND,
            service(FakeProcessingStore(DocumentSourceLookupResult.NotFound), successfulPort()),
        )
        assertRejected(
            DocumentProcessingRejection.CONTENT_UNAVAILABLE,
            service(FakeProcessingStore(DocumentSourceLookupResult.Unavailable), successfulPort()),
        )
        val removed = FakeProcessingStore(foundSource(), saveResult = DocumentExtractionSaveResult.SOURCE_NOT_FOUND)
        assertRejected(DocumentProcessingRejection.DOCUMENT_NOT_FOUND, service(removed, successfulPort()))
        val unavailable = FakeProcessingStore(foundSource(), saveResult = DocumentExtractionSaveResult.UNAVAILABLE)
        assertRejected(DocumentProcessingRejection.STORAGE_UNAVAILABLE, service(unavailable, successfulPort()))
    }

    @Test
    internal fun `processor failures are classified without provider details`() = runTest {
        DocumentOcrFailureKind.entries.forEach { failure ->
            val expected = if (failure in setOf(
                    DocumentOcrFailureKind.INPUT_REJECTED,
                    DocumentOcrFailureKind.INVALID_RESPONSE,
                )
            ) {
                DocumentProcessingRejection.OUTPUT_INVALID
            } else {
                DocumentProcessingRejection.PROCESSOR_UNAVAILABLE
            }
            val port = DocumentOcrPort { DocumentOcrPortResult.Failed(failure) }
            assertRejected(expected, service(FakeProcessingStore(foundSource()), port))
        }
    }

    @Test
    internal fun `malformed and excessive output never reaches storage`() = runTest {
        val cases = listOf(
            listOf(DocumentOcrPage(0, "one")),
            listOf(DocumentOcrPage(1, "one"), DocumentOcrPage(0, "two")),
            listOf(DocumentOcrPage(0, "12345"), DocumentOcrPage(1, "two")),
            listOf(DocumentOcrPage(0, "1234"), DocumentOcrPage(1, "1234")),
        )
        cases.forEachIndexed { index, pages ->
            val store = FakeProcessingStore(foundSource())
            val policy = DocumentOcrPolicy(maximumPages = 2, maximumPageCharacters = 4, maximumTotalCharacters = 6)
            val result = service(store, DocumentOcrPort { DocumentOcrPortResult.Processed("fixture", pages) }, policy)
                .process(DOCUMENT_ID)
            val expected = if (index < 2) {
                DocumentProcessingRejection.OUTPUT_INVALID
            } else {
                DocumentProcessingRejection.OUTPUT_TOO_LARGE
            }
            assertEquals(expected, assertIs<DocumentProcessingResult.Rejected>(result).reason)
            assertNull(store.savedExtraction)
        }
    }

    @Test
    internal fun `clock exception fails closed without publishing output`() = runTest {
        val store = FakeProcessingStore(foundSource())
        val service = DefaultDocumentOcr(
            store = store,
            processor = successfulPort(),
            clock = DocumentProcessingClock { error("clock unavailable") },
        )

        assertRejected(DocumentProcessingRejection.PROCESSOR_UNAVAILABLE, service)
        assertNull(store.savedExtraction)
    }

    @Test
    internal fun `lookup maps durable states and fails closed on exceptions`() = runTest {
        val extraction = extraction()
        val cases = listOf(
            DocumentExtractionStoreLookupResult.Found(extraction) to DocumentExtractionLookupResult.Found(extraction),
            DocumentExtractionStoreLookupResult.NotProcessed to DocumentExtractionLookupResult.NotProcessed,
            DocumentExtractionStoreLookupResult.SourceNotFound to DocumentExtractionLookupResult.DocumentNotFound,
            DocumentExtractionStoreLookupResult.Unavailable to DocumentExtractionLookupResult.StorageUnavailable,
        )
        cases.forEach { (stored, expected) ->
            val service = service(FakeProcessingStore(foundSource(), lookupResult = stored), successfulPort())
            assertEquals(expected, service.find(DOCUMENT_ID))
        }
        val throwing = FakeProcessingStore(foundSource(), throwOnFind = true)
        assertEquals(
            DocumentExtractionLookupResult.StorageUnavailable,
            service(throwing, successfulPort()).find(DOCUMENT_ID),
        )
    }

    @Test
    internal fun `cancellation propagates and concurrent processing is serialized`() = runTest {
        val cancelling = service(
            FakeProcessingStore(foundSource()),
            DocumentOcrPort { throw CancellationException("stop") },
        )
        assertFailsWith<CancellationException> { cancelling.process(DOCUMENT_ID) }

        var active = 0
        var maximumActive = 0
        val serial = service(
            FakeProcessingStore(foundSource()),
            DocumentOcrPort {
                active += 1
                maximumActive = maxOf(maximumActive, active)
                yield()
                active -= 1
                successfulResult()
            },
        )
        val first = async { serial.process(DOCUMENT_ID) }
        val second = async { serial.process(DOCUMENT_ID) }
        first.await()
        second.await()
        assertEquals(1, maximumActive)
    }

    private suspend fun assertRejected(expected: DocumentProcessingRejection, service: DefaultDocumentOcr) {
        val result = assertIs<DocumentProcessingResult.Rejected>(service.process(DOCUMENT_ID))
        assertEquals(expected, result.reason)
    }

    private fun service(
        store: FakeProcessingStore,
        port: DocumentOcrPort,
        policy: DocumentOcrPolicy = DocumentOcrPolicy(),
    ): DefaultDocumentOcr = DefaultDocumentOcr(store, port, DocumentProcessingClock { PROCESSED_AT }, policy)

    private fun successfulPort(): DocumentOcrPort = DocumentOcrPort { successfulResult() }

    private fun successfulResult(): DocumentOcrPortResult = DocumentOcrPortResult.Processed(
        processor = "fixture-ocr-v1",
        pages = listOf(DocumentOcrPage(0, "one"), DocumentOcrPage(1, "two")),
    )

    private fun foundSource(): DocumentSourceLookupResult.Found = DocumentSourceLookupResult.Found(
        StoredDocumentSource(document(), SOURCE_BYTES),
    )

    private fun document(): ImportedDocument = ImportedDocument(
        id = DOCUMENT_ID,
        displayName = "meeting.pdf",
        sizeBytes = SOURCE_BYTES.size.toLong(),
        importedAtEpochMilliseconds = 1,
        content = DocumentContentDescriptor.Pdf(pageCount = 2),
    )

    private fun extraction(): DocumentExtraction = DocumentExtraction(
        sourceDocumentId = DOCUMENT_ID,
        processor = "fixture-ocr-v1",
        processedAtEpochMilliseconds = PROCESSED_AT,
        pages = listOf(
            io.github.artemidius.blowhard.core.api.ExtractedDocumentPage(0, "one"),
            io.github.artemidius.blowhard.core.api.ExtractedDocumentPage(1, "two"),
        ),
    )

    private class FakeProcessingStore(
        private val sourceResult: DocumentSourceLookupResult,
        private val saveResult: DocumentExtractionSaveResult = DocumentExtractionSaveResult.SAVED,
        private val lookupResult: DocumentExtractionStoreLookupResult =
            DocumentExtractionStoreLookupResult.NotProcessed,
        private val throwOnFind: Boolean = false,
    ) : DocumentProcessingStore {
        val source: StoredDocumentSource
            get() = (sourceResult as DocumentSourceLookupResult.Found).source
        var savedExtraction: DocumentExtraction? = null

        override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = sourceResult

        override suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult {
            savedExtraction = extraction
            return saveResult
        }

        override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult {
            if (throwOnFind) error("private storage detail")
            return lookupResult
        }

        override suspend fun close() = Unit
    }

    private companion object {
        val DOCUMENT_ID = DocumentId("document-1")
        val SOURCE_BYTES = byteArrayOf(1, 2, 3)
        const val PROCESSED_AT = 42L
    }
}
