package io.github.artemidius.blowhard.android

import android.content.Context
import io.github.artemidius.blowhard.adapters.documents.PortableDocumentInspector
import io.github.artemidius.blowhard.adapters.persistence.AndroidDocumentProcessingStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidDocumentStores
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
import java.util.UUID

/** Owns Android document storage and portable document application services. */
internal class AndroidDocumentSession private constructor(
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
            context: Context,
            databaseName: String = CONVERSATION_DATABASE_NAME,
            processor: DocumentOcrPort,
        ): AndroidDocumentSession? {
            var documentStore: DocumentStore? = null
            var processingStore: DocumentProcessingStore? = null
            return try {
                val documents = AndroidDocumentStores.open(context, databaseName)
                documentStore = documents
                val processing = AndroidDocumentProcessingStores.open(context, databaseName)
                processingStore = processing
                AndroidDocumentSession(
                    library = DefaultDocumentLibrary(
                        store = documents,
                        inspector = PortableDocumentInspector,
                        idFactory = DocumentIdFactory { DocumentId(UUID.randomUUID().toString()) },
                        clock = DocumentImportClock(System::currentTimeMillis),
                    ),
                    ocr = DefaultDocumentOcr(
                        store = processing,
                        processor = processor,
                        clock = DocumentProcessingClock(System::currentTimeMillis),
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
