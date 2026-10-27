package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.DefaultRunDiagnosticReportGenerator
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-owned portable services and presentation resources.
 *
 * Platform hosts construct one instance outside UI recomposition and call [close] exactly when the
 * application session ends. Native stores and provider clients remain owned by the host that
 * supplied them through [ApplicationRuntimeDependencies].
 */
public class ApplicationRuntime private constructor(
    public val conversationPresenter: ConversationPresenter,
    public val connectionSettingsPresenter: ConnectionSettingsPresenter,
    public val runInspectorPresenter: RunInspectorPresenter,
    public val documentLibraryPresenter: DocumentLibraryPresenter,
    public val meetingNotePresenter: MeetingNotePresenter,
    internal val runs: DefaultConversationRuns,
) {
    private val closeMutex = Mutex()
    private var closed = false

    /** Stops presenters and owned run work once; supplied native dependencies remain host-owned. */
    public suspend fun close() {
        closeMutex.withLock {
            if (closed) return@withLock
            closed = true
            try {
                closePresenters()
            } finally {
                runs.close()
            }
        }
    }

    private fun closePresenters() {
        closeAll(
            documentLibraryPresenter::close,
            meetingNotePresenter::close,
            runInspectorPresenter::close,
            connectionSettingsPresenter::close,
            conversationPresenter::close,
        )
    }

    /** Creates portable application runtimes. */
    public companion object {
        /** Creates one runtime from explicit application and platform boundaries. */
        public fun create(
            dependencies: ApplicationRuntimeDependencies,
            configuration: ApplicationRuntimeConfiguration,
        ): ApplicationRuntime {
            val runs = createRuns(dependencies, configuration)
            val presenters = createPresenters(runs, dependencies, configuration)
            return ApplicationRuntime(
                conversationPresenter = presenters.conversation,
                connectionSettingsPresenter = presenters.connectionSettings,
                runInspectorPresenter = presenters.runInspector,
                documentLibraryPresenter = presenters.documentLibrary,
                meetingNotePresenter = presenters.meetingNotes,
                runs = runs,
            )
        }
    }
}

private fun createRuns(
    dependencies: ApplicationRuntimeDependencies,
    configuration: ApplicationRuntimeConfiguration,
): DefaultConversationRuns = DefaultConversationRuns(
    generation = dependencies.generation,
    idFactory = configuration.runIdFactory,
    clock = configuration.clock,
    coroutineContext = configuration.coroutineContext,
    connectionSettings = dependencies.connectionSettings,
    conversationStore = dependencies.conversationStore,
    documentProcessingStore = dependencies.documentProcessingStore,
)

private fun createPresenters(
    runs: DefaultConversationRuns,
    dependencies: ApplicationRuntimeDependencies,
    configuration: ApplicationRuntimeConfiguration,
): ApplicationPresenters = ApplicationPresenters(
    conversation = ConversationPresenter(
        runs = runs,
        library = runs,
        scenarioSelection = dependencies.scenarioSelection,
        clipboardWriter = dependencies.clipboardWriter,
        coroutineContext = configuration.coroutineContext,
        initialState = configuration.initialState,
    ),
    connectionSettings = ConnectionSettingsPresenter(
        connectionSettings = dependencies.connectionSettings,
        coroutineContext = configuration.coroutineContext,
        autoVerifyCredential = configuration.autoVerifyCredential,
    ),
    runInspector = RunInspectorPresenter(
        inspector = runs,
        reportGenerator = DefaultRunDiagnosticReportGenerator(runs),
        reportSaver = dependencies.reportSaver,
        coroutineContext = configuration.coroutineContext,
    ),
    documentLibrary = DocumentLibraryPresenter(
        library = dependencies.documentLibrary,
        picker = dependencies.documentPicker,
        coroutineContext = configuration.coroutineContext,
        ocr = dependencies.documentOcr,
    ),
    meetingNotes = MeetingNotePresenter(
        notes = dependencies.meetingNotes,
        coroutineContext = configuration.coroutineContext,
        approvals = dependencies.meetingNoteApprovals,
    ),
)

private data class ApplicationPresenters(
    val conversation: ConversationPresenter,
    val connectionSettings: ConnectionSettingsPresenter,
    val runInspector: RunInspectorPresenter,
    val documentLibrary: DocumentLibraryPresenter,
    val meetingNotes: MeetingNotePresenter,
)

private fun closeAll(vararg resources: () -> Unit) {
    fun closeAt(index: Int) {
        if (index == resources.size) return
        try {
            resources[index]()
        } finally {
            closeAt(index + 1)
        }
    }
    closeAt(0)
}
