package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.documents.PortableDocumentInspector
import io.github.artemidius.blowhard.adapters.persistence.DesktopDocumentProcessingStores
import io.github.artemidius.blowhard.adapters.persistence.DesktopDocumentStores
import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.DocumentProcessingRejection
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import io.github.artemidius.blowhard.core.application.DefaultDocumentLibrary
import io.github.artemidius.blowhard.core.application.DefaultDocumentOcr
import io.github.artemidius.blowhard.core.application.DocumentIdFactory
import io.github.artemidius.blowhard.core.application.DocumentImportClock
import io.github.artemidius.blowhard.core.application.DocumentProcessingClock
import io.github.artemidius.blowhard.core.domain.DocumentInspector
import io.github.artemidius.blowhard.core.domain.DocumentOcrFailureKind
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the desktop document library, OCR orchestration, and durable storage resources. */
internal class DesktopDocumentSession private constructor(
    val library: DocumentLibrary,
    val ocr: DocumentOcr,
    val available: Boolean,
    private val store: DocumentStore?,
    private val processingStore: DocumentProcessingStore?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        processingStore.closeSafely()
        store.closeSafely()
    }

    companion object {
        fun open(
            configurationDirectory: Path,
            openStore: (String) -> DocumentStore = DesktopDocumentStores::open,
            openProcessingStore: (String) -> DocumentProcessingStore = DesktopDocumentProcessingStores::open,
            inspector: DocumentInspector = PortableDocumentInspector,
            processor: DocumentOcrPort = UnavailableDocumentOcrPort,
            idFactory: DocumentIdFactory = DocumentIdFactory { DocumentId(UUID.randomUUID().toString()) },
            clock: DocumentImportClock = DocumentImportClock { System.currentTimeMillis() },
            processingClock: DocumentProcessingClock = DocumentProcessingClock { System.currentTimeMillis() },
        ): DesktopDocumentSession {
            var store: DocumentStore? = null
            var processingStore: DocumentProcessingStore? = null
            return try {
                Files.createDirectories(configurationDirectory)
                val databasePath = conversationDatabasePath(configurationDirectory).toString()
                val openedStore = openStore(databasePath)
                store = openedStore
                val openedProcessingStore = openProcessingStore(databasePath)
                processingStore = openedProcessingStore
                DesktopDocumentSession(
                    library = DefaultDocumentLibrary(openedStore, inspector, idFactory, clock),
                    ocr = DefaultDocumentOcr(openedProcessingStore, processor, processingClock),
                    available = true,
                    store = openedStore,
                    processingStore = openedProcessingStore,
                )
            } catch (_: Exception) {
                processingStore.closeSafely()
                store.closeSafely()
                DesktopDocumentSession(
                    UnavailableDocumentLibrary,
                    UnavailableDocumentOcr,
                    available = false,
                    store = null,
                    processingStore = null,
                )
            }
        }
    }
}

internal object UnavailableDocumentOcr : DocumentOcr {
    override suspend fun process(id: DocumentId): DocumentProcessingResult =
        DocumentProcessingResult.Rejected(DocumentProcessingRejection.STORAGE_UNAVAILABLE)

    override suspend fun find(id: DocumentId): DocumentExtractionLookupResult =
        DocumentExtractionLookupResult.StorageUnavailable
}

private object UnavailableDocumentOcrPort : DocumentOcrPort {
    override suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult =
        DocumentOcrPortResult.Failed(DocumentOcrFailureKind.REMOTE_FAILURE)
}

internal object UnavailableDocumentLibrary : DocumentLibrary {
    override suspend fun import(source: DocumentImportSource): DocumentImportResult =
        DocumentImportResult.Rejected(DocumentImportRejection.STORAGE_UNAVAILABLE)

    override suspend fun list(): DocumentListResult = DocumentListResult.StorageUnavailable

    override suspend fun find(id: DocumentId): DocumentLookupResult = DocumentLookupResult.StorageUnavailable

    override suspend fun delete(id: DocumentId): DocumentDeletionResult = DocumentDeletionResult.STORAGE_UNAVAILABLE
}

private fun DocumentStore?.closeSafely() {
    val ownedStore = this ?: return
    try {
        runBlocking { ownedStore.close() }
    } catch (_: Exception) {
        // Startup and shutdown remain safe when a partially opened store cannot close cleanly.
    }
}

private fun DocumentProcessingStore?.closeSafely() {
    val ownedStore = this ?: return
    try {
        runBlocking { ownedStore.close() }
    } catch (_: Exception) {
        // Startup and shutdown remain safe when a partially opened store cannot close cleanly.
    }
}
