package io.github.artemidius.blowhard.android

import android.content.Context
import io.github.artemidius.blowhard.adapters.documents.PortableDocumentInspector
import io.github.artemidius.blowhard.adapters.persistence.AndroidDocumentProcessingStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidDocumentStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidMeetingNoteStores
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.application.DefaultDocumentLibrary
import io.github.artemidius.blowhard.core.application.DefaultDocumentOcr
import io.github.artemidius.blowhard.core.application.DefaultMeetingNotes
import io.github.artemidius.blowhard.core.application.DocumentIdFactory
import io.github.artemidius.blowhard.core.application.DocumentImportClock
import io.github.artemidius.blowhard.core.application.DocumentProcessingClock
import io.github.artemidius.blowhard.core.application.MeetingNoteClock
import io.github.artemidius.blowhard.core.application.MeetingNoteIdFactory
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentStore
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore
import java.util.UUID

/** Owns Android document storage and portable document application services. */
internal class AndroidDocumentSession private constructor(
    val library: DocumentLibrary,
    val ocr: DocumentOcr,
    val meetingNotes: MeetingNotes,
    val processingStore: DocumentProcessingStore,
    val meetingNoteStore: MeetingNoteStore,
    private val documentStore: DocumentStore,
) {
    suspend fun close() {
        try {
            meetingNoteStore.close()
        } finally {
            try {
                processingStore.close()
            } finally {
                documentStore.close()
            }
        }
    }

    companion object {
        suspend fun openOrNull(
            context: Context,
            databaseName: String = CONVERSATION_DATABASE_NAME,
            processor: DocumentOcrPort,
            meetingNoteGeneration: MeetingNoteGenerationPort,
        ): AndroidDocumentSession? {
            var documentStore: DocumentStore? = null
            var processingStore: DocumentProcessingStore? = null
            var meetingNoteStore: MeetingNoteStore? = null
            return try {
                val documents = AndroidDocumentStores.open(context, databaseName)
                documentStore = documents
                val processing = AndroidDocumentProcessingStores.open(context, databaseName)
                processingStore = processing
                val notes = AndroidMeetingNoteStores.open(context, databaseName)
                meetingNoteStore = notes
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
                    meetingNotes = createMeetingNotes(processing, meetingNoteGeneration, notes),
                    processingStore = processing,
                    meetingNoteStore = notes,
                    documentStore = documents,
                )
            } catch (_: Exception) {
                meetingNoteStore.closeSafely()
                processingStore.closeSafely()
                documentStore.closeSafely()
                null
            }
        }
    }
}

private fun createMeetingNotes(
    processingStore: DocumentProcessingStore,
    generation: MeetingNoteGenerationPort,
    noteStore: MeetingNoteStore,
): MeetingNotes = DefaultMeetingNotes(
    documentStore = processingStore,
    generator = generation,
    noteStore = noteStore,
    idFactory = MeetingNoteIdFactory { MeetingNoteId(UUID.randomUUID().toString()) },
    clock = MeetingNoteClock(System::currentTimeMillis),
)

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

private suspend fun MeetingNoteStore?.closeSafely() {
    try {
        this?.close()
    } catch (_: Exception) {
        // A partially opened optional capability must not prevent conversation startup.
    }
}
