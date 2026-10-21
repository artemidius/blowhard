package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.application.DefaultMeetingNotes
import io.github.artemidius.blowhard.core.application.MeetingNoteClock
import io.github.artemidius.blowhard.core.application.MeetingNoteIdFactory
import java.util.UUID

internal fun createMeetingNotes(
    documents: DesktopDocumentSession,
    generation: DesktopMeetingNoteGenerationRouter,
): MeetingNotes {
    val processingStore = documents.processingStore
    val noteStore = documents.meetingNoteStore
    return if (processingStore == null || noteStore == null) {
        UnavailableMeetingNotes
    } else {
        DefaultMeetingNotes(
            documentStore = processingStore,
            generator = generation,
            noteStore = noteStore,
            idFactory = MeetingNoteIdFactory { MeetingNoteId(UUID.randomUUID().toString()) },
            clock = MeetingNoteClock { System.currentTimeMillis() },
        )
    }
}

internal fun closeDesktopProviders(
    generation: DesktopGenerationRouter,
    meetingNotes: DesktopMeetingNoteGenerationRouter,
    providers: DesktopProviderComposition,
) {
    try {
        generation.close()
    } finally {
        try {
            meetingNotes.close()
        } finally {
            try {
                providers.documentOcr.close()
            } finally {
                providers.modelCatalog.close()
            }
        }
    }
}
