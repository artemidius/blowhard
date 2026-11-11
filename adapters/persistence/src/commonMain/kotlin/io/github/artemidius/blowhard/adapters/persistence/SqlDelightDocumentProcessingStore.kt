package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.db.SqlDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.StoredDocumentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SqlDelightDocumentProcessingStore(private val driver: SqlDriver) : DocumentProcessingStore {
    private val queries = BlowhardDatabase(driver).documentExtractionQueries
    private val access = Mutex()
    private var closed = false

    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult =
        safely(DocumentSourceLookupResult.Unavailable) {
            queries.findSource(id.value) {
                    sourceId,
                    name,
                    size,
                    importedAt,
                    format,
                    pageCount,
                    width,
                    height,
                    content,
                ->
                StoredDocumentSource(
                    document = toImportedDocument(sourceId, name, size, importedAt, format, pageCount, width, height),
                    content = content,
                )
            }.executeAsOneOrNull()?.let(DocumentSourceLookupResult::Found)
                ?: DocumentSourceLookupResult.NotFound
        }

    override suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult =
        safely(DocumentExtractionSaveResult.UNAVAILABLE) {
            var result = DocumentExtractionSaveResult.SOURCE_NOT_FOUND
            queries.transaction {
                if (queries.sourceExists(extraction.sourceDocumentId.value).executeAsOne()) {
                    queries.replace(extraction)
                    result = DocumentExtractionSaveResult.SAVED
                }
            }
            result
        }

    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult =
        safely(DocumentExtractionStoreLookupResult.Unavailable) {
            if (!queries.sourceExists(id.value).executeAsOne()) {
                return@safely DocumentExtractionStoreLookupResult.SourceNotFound
            }
            queries.findExtraction(id.value) { sourceId, processor, processedAt ->
                StoredExtractionHeader(DocumentId(sourceId), processor, processedAt)
            }.executeAsOneOrNull()?.let { header ->
                val pages = queries.findExtractionPages(id.value) { index, markdown ->
                    ExtractedDocumentPage(index.toInt(), markdown)
                }.executeAsList()
                DocumentExtractionStoreLookupResult.Found(
                    DocumentExtraction(header.sourceId, header.processor, header.processedAt, pages),
                )
            } ?: DocumentExtractionStoreLookupResult.NotProcessed
        }

    override suspend fun close(): Unit = access.withLock {
        if (closed) return
        closed = true
        driver.close()
    }

    private suspend fun <T> safely(fallback: T, operation: () -> T): T = try {
        access.withLock { if (closed) fallback else operation() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
}

private fun io.github.artemidius.blowhard.adapters.persistence.db.DocumentExtractionQueries.replace(
    extraction: DocumentExtraction,
) {
    val sourceId = extraction.sourceDocumentId.value
    deleteExtractionPages(sourceId).value
    deleteExtraction(sourceId).value
    insertExtraction(sourceId, extraction.processor, extraction.processedAtEpochMilliseconds).value
    extraction.pages.forEach { page ->
        insertExtractionPage(sourceId, page.index.toLong(), page.markdown).value
    }
}

private data class StoredExtractionHeader(val sourceId: DocumentId, val processor: String, val processedAt: Long)
