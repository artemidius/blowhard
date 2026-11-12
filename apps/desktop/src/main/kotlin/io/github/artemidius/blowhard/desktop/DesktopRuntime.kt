package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.DefaultRunDiagnosticReportGenerator
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenarioSelection
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalPort
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import io.github.artemidius.blowhard.ui.presentation.UnavailableMeetingNoteApprovalPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopRuntime private constructor(
    val presenter: ConversationPresenter,
    val connectionSettingsPresenter: ConnectionSettingsPresenter,
    val runInspectorPresenter: RunInspectorPresenter,
    val documentLibraryPresenter: DocumentLibraryPresenter,
    val meetingNotePresenter: MeetingNotePresenter,
    private val runs: DefaultConversationRuns,
    private val closeHistoryResources: () -> Unit,
    private val closeProviderResources: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            documentLibraryPresenter.close()
            meetingNotePresenter.close()
            runInspectorPresenter.close()
            connectionSettingsPresenter.close()
            presenter.close()
            runBlocking { runs.close() }
        } finally {
            try {
                closeHistoryResources()
            } finally {
                closeProviderResources()
            }
        }
    }

    companion object {
        fun create(
            clipboardWriter: ClipboardWriter,
            connectionSettings: ConnectionSettings,
            generation: TextGenerationPort,
            scenarioSelection: DemoScenarioSelection,
            autoVerifyCredential: Boolean = false,
            reportSaver: RunDiagnosticReportSaver = RunDiagnosticReportSaver {
                RunDiagnosticReportSaveResult.UNAVAILABLE
            },
            initialState: ConversationUiState = emptyDesktopConversationState(),
            conversationStore: ConversationStore? = null,
            closeHistoryResources: () -> Unit = {},
            closeProviderResources: () -> Unit = {},
            documentLibrary: DocumentLibrary = UnavailableDocumentLibrary,
            documentOcr: DocumentOcr = UnavailableDocumentOcr,
            documentProcessingStore: DocumentProcessingStore? = null,
            documentPicker: DocumentPicker = DocumentPicker { null },
            closeDocumentResources: () -> Unit = {},
            meetingNotes: MeetingNotes = UnavailableMeetingNotes,
            meetingNoteApprovals: MeetingNoteApprovalPort = UnavailableMeetingNoteApprovalPort,
        ): DesktopRuntime {
            val runs = createRuns(generation, connectionSettings, conversationStore, documentProcessingStore)
            val presenters = createPresenters(
                runs = runs,
                connectionSettings = connectionSettings,
                scenarioSelection = scenarioSelection,
                clipboardWriter = clipboardWriter,
                reportSaver = reportSaver,
                initialState = initialState,
                autoVerifyCredential = autoVerifyCredential,
                documentLibrary = documentLibrary,
                documentOcr = documentOcr,
                documentPicker = documentPicker,
                meetingNotes = meetingNotes,
                meetingNoteApprovals = meetingNoteApprovals,
            )
            return DesktopRuntime(
                presenters.conversation,
                presenters.connectionSettings,
                presenters.runInspector,
                presenters.documentLibrary,
                presenters.meetingNotes,
                runs,
                closeHistoryResources = {
                    try {
                        closeHistoryResources()
                    } finally {
                        closeDocumentResources()
                    }
                },
                closeProviderResources,
            )
        }
    }
}

private fun createRuns(
    generation: TextGenerationPort,
    connectionSettings: ConnectionSettings,
    conversationStore: ConversationStore?,
    documentProcessingStore: DocumentProcessingStore?,
): DefaultConversationRuns = DefaultConversationRuns(
    generation = generation,
    idFactory = SequentialRunIdFactory("desktop-${UUID.randomUUID()}"),
    clock = SystemRunClock,
    coroutineContext = Dispatchers.Default,
    connectionSettings = connectionSettings,
    conversationStore = conversationStore,
    documentProcessingStore = documentProcessingStore,
)

private fun createPresenters(
    runs: DefaultConversationRuns,
    connectionSettings: ConnectionSettings,
    scenarioSelection: DemoScenarioSelection,
    clipboardWriter: ClipboardWriter,
    reportSaver: RunDiagnosticReportSaver,
    initialState: ConversationUiState,
    autoVerifyCredential: Boolean,
    documentLibrary: DocumentLibrary,
    documentOcr: DocumentOcr,
    documentPicker: DocumentPicker,
    meetingNotes: MeetingNotes,
    meetingNoteApprovals: MeetingNoteApprovalPort,
): DesktopPresenters = DesktopPresenters(
    conversation = ConversationPresenter(
        runs = runs,
        library = runs,
        scenarioSelection = scenarioSelection,
        clipboardWriter = clipboardWriter,
        coroutineContext = Dispatchers.Default,
        initialState = initialState,
    ),
    connectionSettings = ConnectionSettingsPresenter(
        connectionSettings = connectionSettings,
        coroutineContext = Dispatchers.Default,
        autoVerifyCredential = autoVerifyCredential,
    ),
    runInspector = RunInspectorPresenter(
        inspector = runs,
        reportGenerator = DefaultRunDiagnosticReportGenerator(runs),
        reportSaver = reportSaver,
        coroutineContext = Dispatchers.Default,
    ),
    documentLibrary = DocumentLibraryPresenter(
        library = documentLibrary,
        picker = documentPicker,
        coroutineContext = Dispatchers.Default,
        ocr = documentOcr,
    ),
    meetingNotes = MeetingNotePresenter(meetingNotes, Dispatchers.Default, meetingNoteApprovals),
)

private data class DesktopPresenters(
    val conversation: ConversationPresenter,
    val connectionSettings: ConnectionSettingsPresenter,
    val runInspector: RunInspectorPresenter,
    val documentLibrary: DocumentLibraryPresenter,
    val meetingNotes: MeetingNotePresenter,
)

internal object UnavailableMeetingNotes : MeetingNotes {
    override suspend fun generate(sourceDocumentId: io.github.artemidius.blowhard.core.api.DocumentId) =
        MeetingNoteGenerationOutcome.Rejected(MeetingNoteGenerationRejection.STORAGE_UNAVAILABLE)

    override suspend fun find(id: MeetingNoteId): MeetingNoteLookupOutcome = MeetingNoteLookupOutcome.StorageUnavailable

    override suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        content: io.github.artemidius.blowhard.core.api.MeetingNoteContent,
    ): MeetingNoteUpdateOutcome = MeetingNoteUpdateOutcome.StorageUnavailable

    override suspend fun export(id: MeetingNoteId, format: MeetingNoteExportFormat): MeetingNoteExportOutcome =
        MeetingNoteExportOutcome.StorageUnavailable
}
