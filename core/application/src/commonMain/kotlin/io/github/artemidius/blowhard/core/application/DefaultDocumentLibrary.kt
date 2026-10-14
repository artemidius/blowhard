package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentInspector
import io.github.artemidius.blowhard.core.domain.DocumentSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentStore
import io.github.artemidius.blowhard.core.domain.DocumentStoreDeletionResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreListResult
import io.github.artemidius.blowhard.core.domain.DocumentStoreLookupResult
import kotlinx.coroutines.CancellationException

/** Default bounded document lifecycle implementation. */
public class DefaultDocumentLibrary(
    private val store: DocumentStore,
    inspector: DocumentInspector,
    private val idFactory: DocumentIdFactory,
    private val clock: DocumentImportClock,
    policy: DocumentImportPolicy = DocumentImportPolicy(),
) : DocumentLibrary {
    private val preparer = DocumentImportPreparer(inspector, policy)

    override suspend fun import(source: DocumentImportSource): DocumentImportResult = try {
        importSafely(source)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        rejected(DocumentImportRejection.STORAGE_UNAVAILABLE)
    }

    private suspend fun importSafely(source: DocumentImportSource): DocumentImportResult =
        when (val preparation = preparer.prepare(source)) {
            is DocumentImportPreparation.Accepted -> store(preparation)
            is DocumentImportPreparation.Rejected -> rejected(preparation.reason)
        }

    override suspend fun list(): DocumentListResult = try {
        when (val result = store.list()) {
            is DocumentStoreListResult.Available -> DocumentListResult.Available(result.documents)
            DocumentStoreListResult.Unavailable -> DocumentListResult.StorageUnavailable
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentListResult.StorageUnavailable
    }

    override suspend fun find(id: DocumentId): DocumentLookupResult = try {
        when (val result = store.find(id)) {
            is DocumentStoreLookupResult.Found -> DocumentLookupResult.Found(result.document)
            DocumentStoreLookupResult.NotFound -> DocumentLookupResult.NotFound
            DocumentStoreLookupResult.Unavailable -> DocumentLookupResult.StorageUnavailable
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentLookupResult.StorageUnavailable
    }

    override suspend fun delete(id: DocumentId): DocumentDeletionResult = try {
        when (store.delete(id)) {
            DocumentStoreDeletionResult.DELETED -> DocumentDeletionResult.DELETED
            DocumentStoreDeletionResult.NOT_FOUND -> DocumentDeletionResult.NOT_FOUND
            DocumentStoreDeletionResult.UNAVAILABLE -> DocumentDeletionResult.STORAGE_UNAVAILABLE
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentDeletionResult.STORAGE_UNAVAILABLE
    }

    private suspend fun save(document: ImportedDocument, bytes: ByteArray): DocumentSaveResult = try {
        store.save(document, bytes.copyOf())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentSaveResult.UNAVAILABLE
    }

    private suspend fun store(preparation: DocumentImportPreparation.Accepted): DocumentImportResult {
        val document = ImportedDocument(
            id = idFactory.create(),
            displayName = preparation.displayName,
            sizeBytes = preparation.bytes.size.toLong(),
            importedAtEpochMilliseconds = clock.nowEpochMilliseconds(),
            content = preparation.descriptor,
        )
        return when (save(document, preparation.bytes)) {
            DocumentSaveResult.SAVED -> DocumentImportResult.Imported(document)

            DocumentSaveResult.ID_CONFLICT, DocumentSaveResult.UNAVAILABLE ->
                rejected(DocumentImportRejection.STORAGE_UNAVAILABLE)
        }
    }

    private companion object {
        fun rejected(reason: DocumentImportRejection): DocumentImportResult = DocumentImportResult.Rejected(reason)
    }
}
