package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.documents.PortableDocumentInspector
import io.github.artemidius.blowhard.adapters.persistence.DesktopDocumentStores
import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.application.DefaultDocumentLibrary
import io.github.artemidius.blowhard.core.application.DocumentIdFactory
import io.github.artemidius.blowhard.core.application.DocumentImportClock
import io.github.artemidius.blowhard.core.domain.DocumentInspector
import io.github.artemidius.blowhard.core.domain.DocumentStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the desktop document library and its durable storage resources. */
internal class DesktopDocumentSession private constructor(
    val library: DocumentLibrary,
    val available: Boolean,
    private val store: DocumentStore?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        store.closeSafely()
    }

    companion object {
        fun open(
            configurationDirectory: Path,
            openStore: (String) -> DocumentStore = DesktopDocumentStores::open,
            inspector: DocumentInspector = PortableDocumentInspector,
            idFactory: DocumentIdFactory = DocumentIdFactory { DocumentId(UUID.randomUUID().toString()) },
            clock: DocumentImportClock = DocumentImportClock { System.currentTimeMillis() },
        ): DesktopDocumentSession {
            var store: DocumentStore? = null
            return try {
                Files.createDirectories(configurationDirectory)
                val openedStore = openStore(conversationDatabasePath(configurationDirectory).toString())
                store = openedStore
                DesktopDocumentSession(
                    library = DefaultDocumentLibrary(openedStore, inspector, idFactory, clock),
                    available = true,
                    store = openedStore,
                )
            } catch (_: Exception) {
                store.closeSafely()
                DesktopDocumentSession(UnavailableDocumentLibrary, available = false, store = null)
            }
        }
    }
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
