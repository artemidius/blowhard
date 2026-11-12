package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
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
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
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
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopRuntime private constructor(
    private val applicationRuntime: ApplicationRuntime,
    private val closeHistoryResources: () -> Unit,
    private val closeProviderResources: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val presenter: ConversationPresenter = applicationRuntime.conversationPresenter

    val connectionSettingsPresenter: ConnectionSettingsPresenter = applicationRuntime.connectionSettingsPresenter

    val runInspectorPresenter: RunInspectorPresenter = applicationRuntime.runInspectorPresenter

    val documentLibraryPresenter: DocumentLibraryPresenter = applicationRuntime.documentLibraryPresenter

    val meetingNotePresenter: MeetingNotePresenter = applicationRuntime.meetingNotePresenter

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            runBlocking { applicationRuntime.close() }
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
            val applicationRuntime = ApplicationRuntime.create(
                dependencies = ApplicationRuntimeDependencies(
                    connectionSettings = connectionSettings,
                    generation = generation,
                    scenarioSelection = scenarioSelection,
                    clipboardWriter = clipboardWriter,
                    reportSaver = reportSaver,
                    documentLibrary = documentLibrary,
                    documentOcr = documentOcr,
                    documentPicker = documentPicker,
                    meetingNotes = meetingNotes,
                    meetingNoteApprovals = meetingNoteApprovals,
                    conversationStore = conversationStore,
                    documentProcessingStore = documentProcessingStore,
                ),
                configuration = ApplicationRuntimeConfiguration(
                    initialState = initialState,
                    runIdFactory = SequentialRunIdFactory("desktop-${UUID.randomUUID()}"),
                    autoVerifyCredential = autoVerifyCredential,
                ),
            )
            return DesktopRuntime(
                applicationRuntime,
                closeHistoryResources = {
                    try {
                        closeHistoryResources()
                    } finally {
                        closeDocumentResources()
                    }
                },
                closeProviderResources = closeProviderResources,
            )
        }
    }
}

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
