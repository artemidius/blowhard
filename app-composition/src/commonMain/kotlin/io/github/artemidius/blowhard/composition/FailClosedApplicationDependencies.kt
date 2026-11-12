package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionSettings
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
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteExportOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteId
import io.github.artemidius.blowhard.core.api.MeetingNoteLookupOutcome
import io.github.artemidius.blowhard.core.api.MeetingNoteUpdateOutcome
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import io.github.artemidius.blowhard.ui.presentation.UnavailableMeetingNoteApprovalPort
import kotlinx.coroutines.flow.flowOf

internal fun failClosedDependencies(
    connectionSettings: ConnectionSettings,
    generation: TextGenerationPort = FailClosedGeneration,
): ApplicationRuntimeDependencies = ApplicationRuntimeDependencies(
    connectionSettings = connectionSettings,
    generation = generation,
    scenarioSelection = { _, _ -> },
    clipboardWriter = UnavailableClipboard,
    reportSaver = UnavailableReportSaver,
    documentLibrary = UnavailableDocumentLibrary,
    documentOcr = UnavailableDocumentOcr,
    documentPicker = UnavailableDocumentPicker,
    meetingNotes = UnavailableMeetingNotes,
    meetingNoteApprovals = UnavailableMeetingNoteApprovalPort,
)

private object FailClosedGeneration : TextGenerationPort {
    override fun generate(request: io.github.artemidius.blowhard.core.domain.GenerationRequest) = flowOf(
        GenerationEvent.Failed(GenerationFailure(GenerationFailureKind.CREDENTIAL_UNAVAILABLE)),
    )
}

private object UnavailableClipboard : ClipboardWriter {
    override fun write(text: String): ClipboardWriteResult = ClipboardWriteResult.UNAVAILABLE
}

private object UnavailableReportSaver : RunDiagnosticReportSaver {
    override suspend fun save(report: io.github.artemidius.blowhard.core.api.RunDiagnosticReport) =
        RunDiagnosticReportSaveResult.UNAVAILABLE
}

private object UnavailableDocumentPicker : DocumentPicker {
    override suspend fun select(): DocumentImportSource? = null
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
