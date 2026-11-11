package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.db.SqlDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentStore
import io.github.artemidius.blowhard.core.domain.DocumentStoreDeletionResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreListResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SqlDelightDocumentStore(private val driver: SqlDriver) : DocumentStore {
    private val database = BlowhardDatabase(driver)
    private val queries = database.documentQueries
    private val access = Mutex()
    private var closed = false

    override suspend fun save(document: ImportedDocument, content: ByteArray): DocumentSaveResult {
        val isolatedContent = content.copyOf()
        if (isolatedContent.size.toLong() != document.sizeBytes) return DocumentSaveResult.UNAVAILABLE
        return safely(DocumentSaveResult.UNAVAILABLE) {
            if (queries.insert(document, isolatedContent) > 0) {
                DocumentSaveResult.SAVED
            } else {
                DocumentSaveResult.ID_CONFLICT
            }
        }
    }

    override suspend fun list(): DocumentStoreListResult = safely(DocumentStoreListResult.Unavailable) {
        DocumentStoreListResult.Available(queries.listDocuments(::toImportedDocument).executeAsList())
    }

    override suspend fun find(id: DocumentId): DocumentStoreLookupResult =
        safely(DocumentStoreLookupResult.Unavailable) {
            queries.findDocument(id.value, ::toImportedDocument).executeAsOneOrNull()?.let {
                DocumentStoreLookupResult.Found(it)
            } ?: DocumentStoreLookupResult.NotFound
        }

    override suspend fun delete(id: DocumentId): DocumentStoreDeletionResult =
        safely(DocumentStoreDeletionResult.UNAVAILABLE) {
            var deleted = false
            queries.transaction {
                database.meetingNoteQueries.deleteMeetingNotesByDocument(id.value).value
                database.documentExtractionQueries.deleteExtractionPages(id.value).value
                database.documentExtractionQueries.deleteExtraction(id.value).value
                deleted = queries.deleteDocument(id.value).value > 0
            }
            if (deleted) {
                DocumentStoreDeletionResult.DELETED
            } else {
                DocumentStoreDeletionResult.NOT_FOUND
            }
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

private fun io.github.artemidius.blowhard.adapters.persistence.db.DocumentQueries.insert(
    document: ImportedDocument,
    content: ByteArray,
): Long {
    val descriptor = document.content
    return insertDocument(
        id = document.id.value,
        display_name = document.displayName,
        size_bytes = document.sizeBytes,
        imported_at_epoch_ms = document.importedAtEpochMilliseconds,
        format = descriptor.format.name,
        page_count = (descriptor as? DocumentContentDescriptor.Pdf)?.pageCount?.toLong(),
        width_pixels = (descriptor as? DocumentContentDescriptor.Image)?.widthPixels?.toLong(),
        height_pixels = (descriptor as? DocumentContentDescriptor.Image)?.heightPixels?.toLong(),
        content = content,
    ).value
}

internal fun toImportedDocument(
    id: String,
    displayName: String,
    sizeBytes: Long,
    importedAtEpochMilliseconds: Long,
    format: String,
    pageCount: Long?,
    widthPixels: Long?,
    heightPixels: Long?,
): ImportedDocument = ImportedDocument(
    id = DocumentId(id),
    displayName = displayName,
    sizeBytes = sizeBytes,
    importedAtEpochMilliseconds = importedAtEpochMilliseconds,
    content = when (DocumentFormat.valueOf(format)) {
        DocumentFormat.PDF -> DocumentContentDescriptor.Pdf(requireNotNull(pageCount).toInt())

        DocumentFormat.PNG,
        DocumentFormat.JPEG,
        -> DocumentContentDescriptor.Image(
            format = DocumentFormat.valueOf(format),
            widthPixels = requireNotNull(widthPixels).toInt(),
            heightPixels = requireNotNull(heightPixels).toInt(),
        )
    },
)
