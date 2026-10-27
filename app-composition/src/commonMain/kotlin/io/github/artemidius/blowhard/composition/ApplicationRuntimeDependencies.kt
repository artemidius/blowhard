package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.MeetingNotes
import io.github.artemidius.blowhard.core.application.RunClock
import io.github.artemidius.blowhard.core.application.RunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenarioSelection
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteApprovalPort
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/** Application services and native capabilities required by the portable composition root. */
public class ApplicationRuntimeDependencies(
    public val connectionSettings: ConnectionSettings,
    public val generation: TextGenerationPort,
    public val scenarioSelection: DemoScenarioSelection,
    public val clipboardWriter: ClipboardWriter,
    public val reportSaver: RunDiagnosticReportSaver,
    public val documentLibrary: DocumentLibrary,
    public val documentOcr: DocumentOcr,
    public val documentPicker: DocumentPicker,
    public val meetingNotes: MeetingNotes,
    public val meetingNoteApprovals: MeetingNoteApprovalPort,
    public val conversationStore: ConversationStore? = null,
    public val documentProcessingStore: DocumentProcessingStore? = null,
) {
    /** Creates an explicit inactive graph for hosts that do not yet provide native capabilities. */
    public companion object {
        /** Uses [connectionSettings] and rejects every unavailable platform or provider operation. */
        public fun failClosed(connectionSettings: ConnectionSettings): ApplicationRuntimeDependencies =
            failClosedDependencies(connectionSettings)
    }
}

/** Process-specific values controlling one portable application runtime. */
public data class ApplicationRuntimeConfiguration(
    public val initialState: ConversationUiState,
    public val runIdFactory: RunIdFactory,
    public val clock: RunClock = SystemRunClock,
    public val coroutineContext: CoroutineContext = Dispatchers.Default,
    public val autoVerifyCredential: Boolean = false,
)
