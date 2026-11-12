package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ApplicationRuntimeDependenciesTest {
    @Test
    fun `conversation-only graph preserves required ports and rejects optional capabilities`() = runTest {
        val settings = FailClosedConnectionSettings()
        val generation = TextGenerationPort { flowOf(GenerationEvent.Completed) }
        val store = RecordingStore()

        val dependencies = ApplicationRuntimeDependencies.conversationOnly(settings, generation, store)

        assertSame(settings, dependencies.connectionSettings)
        assertSame(generation, dependencies.generation)
        assertSame(store, dependencies.conversationStore)
        assertEquals(ClipboardWriteResult.UNAVAILABLE, dependencies.clipboardWriter.write("text"))
        assertEquals(DocumentListResult.StorageUnavailable, dependencies.documentLibrary.list())
        assertEquals(
            MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE),
            dependencies.meetingNotes.generate(io.github.artemidius.blowhard.core.api.DocumentId("document")),
        )
    }
}
