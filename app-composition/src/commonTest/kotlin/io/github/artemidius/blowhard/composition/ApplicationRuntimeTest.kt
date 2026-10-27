package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
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
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiModel
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import io.github.artemidius.blowhard.ui.presentation.UnavailableMeetingNoteApprovalPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApplicationRuntimeTest {
    @Test
    fun `runtime closes once and rejects future attempts`() = runTest {
        val runtime = runtime(TextGenerationPort { flowOf(GenerationEvent.Completed) })

        runtime.close()
        runtime.close()

        val rejected = assertIs<StartRunResult.Rejected>(runtime.runs.start(request()))
        assertEquals(StartRunRejection.LIFECYCLE_CLOSED, rejected.reason)
    }

    @Test
    fun `one conversation cannot start duplicate provider work`() = runTest {
        val providerStarted = CompletableDeferred<Unit>()
        val releaseProvider = CompletableDeferred<Unit>()
        val runtime = runtime(
            TextGenerationPort {
                flow {
                    providerStarted.complete(Unit)
                    releaseProvider.await()
                    emit(GenerationEvent.Completed)
                }
            },
        )
        try {
            assertIs<StartRunResult.Started>(runtime.runs.start(request()))
            providerStarted.await()

            val duplicate = assertIs<StartRunResult.Rejected>(runtime.runs.start(request()))

            assertEquals(StartRunRejection.CONVERSATION_BUSY, duplicate.reason)
            releaseProvider.complete(Unit)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `runtime uses the injected live provider without fallback`() = runTest {
        val received = CompletableDeferred<GenerationRequest>()
        val runtime = runtime(
            TextGenerationPort { request ->
                received.complete(request)
                flowOf(GenerationEvent.Completed)
            },
        )
        try {
            val started = assertIs<StartRunResult.Started>(runtime.runs.start(request()))

            assertEquals(GenerationTarget.Live(MODEL_ID), started.target)
            assertEquals(GenerationTarget.Live(MODEL_ID), received.await().target)
        } finally {
            runtime.close()
        }
    }

    private fun TestScope.runtime(generation: TextGenerationPort): ApplicationRuntime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies(
            connectionSettings = ReadyConnectionSettings,
            generation = generation,
            scenarioSelection = { _, _ -> error("Live runs must not prepare a fixture") },
            clipboardWriter = ClipboardWriter { ClipboardWriteResult.COPIED },
            reportSaver = RunDiagnosticReportSaver { RunDiagnosticReportSaveResult.UNAVAILABLE },
            documentLibrary = UnavailableDocumentLibrary,
            documentOcr = UnavailableDocumentOcr,
            documentPicker = DocumentPicker { null },
            meetingNotes = UnavailableMeetingNotes,
            meetingNoteApprovals = UnavailableMeetingNoteApprovalPort,
        ),
        configuration = ApplicationRuntimeConfiguration(
            initialState = initialState(),
            runIdFactory = SequentialRunIdFactory("test-runtime"),
            coroutineContext = StandardTestDispatcher(testScheduler),
        ),
    )
}

private fun request(): StartRunRequest = StartRunRequest(CONVERSATION_ID, "Use the configured provider")

private fun initialState(): ConversationUiState = ConversationUiState(
    conversations = listOf(ConversationUiModel(CONVERSATION_ID, "Test conversation")),
    selectedConversationId = CONVERSATION_ID,
)

private object ReadyConnectionSettings : ConnectionSettings {
    private val mutableState = MutableStateFlow(
        ConnectionSettingsState(
            mode = GenerationMode.LIVE,
            credentialStatus = CredentialStatus.CONFIGURED,
            connectionStatus = ConnectionStatus.READY,
            models = listOf(SelectableModel(MODEL_ID, 32_000)),
            selectedModelId = MODEL_ID,
            liveDataDisclosureAccepted = true,
        ),
    )
    override val state: StateFlow<ConnectionSettingsState> = mutableState.asStateFlow()

    override suspend fun refreshCredentialStatus(): Unit = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun testConnection(): ConnectionTestResult =
        ConnectionTestResult.Failure(ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE)

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.NOT_AVAILABLE

    override suspend fun updateGenerationParameters(parameters: GenerationParameters): Unit = Unit

    override suspend fun acknowledgeLiveDataDisclosure(): Unit = Unit

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.LIVE_NOT_READY
}

private object UnavailableDocumentLibrary : DocumentLibrary {
    override suspend fun import(source: DocumentImportSource): DocumentImportResult =
        DocumentImportResult.Rejected(DocumentImportRejection.STORAGE_UNAVAILABLE)

    override suspend fun list(): DocumentListResult = DocumentListResult.StorageUnavailable

    override suspend fun find(id: DocumentId): DocumentLookupResult = DocumentLookupResult.StorageUnavailable

    override suspend fun delete(id: DocumentId): DocumentDeletionResult = DocumentDeletionResult.STORAGE_UNAVAILABLE
}

private object UnavailableDocumentOcr : DocumentOcr {
    override suspend fun process(id: DocumentId): DocumentProcessingResult =
        DocumentProcessingResult.Rejected(DocumentProcessingRejection.STORAGE_UNAVAILABLE)

    override suspend fun find(id: DocumentId): DocumentExtractionLookupResult =
        DocumentExtractionLookupResult.StorageUnavailable
}

private object UnavailableMeetingNotes : MeetingNotes {
    override suspend fun generate(sourceDocumentId: DocumentId): MeetingNoteGenerationOutcome =
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

private val CONVERSATION_ID = ConversationId("runtime-test")
private const val MODEL_ID = "test-live-model"
