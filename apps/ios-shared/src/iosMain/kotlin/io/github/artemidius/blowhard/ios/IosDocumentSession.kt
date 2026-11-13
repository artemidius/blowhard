package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.documents.PortableDocumentInspector
import io.github.artemidius.blowhard.adapters.persistence.NativeDocumentProcessingStores
import io.github.artemidius.blowhard.adapters.persistence.NativeDocumentStores
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.application.DefaultDocumentLibrary
import io.github.artemidius.blowhard.core.application.DefaultDocumentOcr
import io.github.artemidius.blowhard.core.application.DocumentIdFactory
import io.github.artemidius.blowhard.core.application.DocumentImportClock
import io.github.artemidius.blowhard.core.application.DocumentProcessingClock
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentStore
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/** Owns native iOS document storage and portable document application services. */
internal class IosDocumentSession private constructor(
    val library: DocumentLibrary,
    val ocr: DocumentOcr,
    val processingStore: DocumentProcessingStore,
    private val documentStore: DocumentStore,
) {
    suspend fun close() {
        try {
            processingStore.close()
        } finally {
            documentStore.close()
        }
    }

    companion object {
        suspend fun openOrNull(
            databaseName: String = CONVERSATION_DATABASE_NAME,
            processor: DocumentOcrPort,
        ): IosDocumentSession? {
            var documentStore: DocumentStore? = null
            var processingStore: DocumentProcessingStore? = null
            return try {
                val documents = NativeDocumentStores.open(databaseName)
                documentStore = documents
                val processing = NativeDocumentProcessingStores.open(databaseName)
                processingStore = processing
                IosDocumentSession(
                    library = DefaultDocumentLibrary(
                        store = documents,
                        inspector = PortableDocumentInspector,
                        idFactory = DocumentIdFactory { DocumentId(NSUUID().UUIDString) },
                        clock = DocumentImportClock(::currentTimeMillis),
                    ),
                    ocr = DefaultDocumentOcr(
                        store = processing,
                        processor = processor,
                        clock = DocumentProcessingClock(::currentTimeMillis),
                    ),
                    processingStore = processing,
                    documentStore = documents,
                )
            } catch (_: Exception) {
                processingStore.closeSafely()
                documentStore.closeSafely()
                null
            }
        }
    }
}

private fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970() * MILLISECONDS_PER_SECOND).toLong()

private suspend fun DocumentProcessingStore?.closeSafely() {
    try {
        this?.close()
    } catch (_: Exception) {
        // A partially opened optional capability must not prevent conversation startup.
    }
}

private suspend fun DocumentStore?.closeSafely() {
    try {
        this?.close()
    } catch (_: Exception) {
        // A partially opened optional capability must not prevent conversation startup.
    }
}

private const val MILLISECONDS_PER_SECOND = 1_000.0
