package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.DefaultRunDiagnosticReportGenerator
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenarioSelection
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopRuntime private constructor(
    val presenter: ConversationPresenter,
    val connectionSettingsPresenter: ConnectionSettingsPresenter,
    val runInspectorPresenter: RunInspectorPresenter,
    private val runs: DefaultConversationRuns,
    private val closeHistoryResources: () -> Unit,
    private val closeProviderResources: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
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
            reportSaver: RunDiagnosticReportSaver = RunDiagnosticReportSaver {
                RunDiagnosticReportSaveResult.UNAVAILABLE
            },
            initialState: ConversationUiState = emptyDesktopConversationState(),
            conversationStore: ConversationStore? = null,
            closeHistoryResources: () -> Unit = {},
            closeProviderResources: () -> Unit = {},
        ): DesktopRuntime {
            val runs = DefaultConversationRuns(
                generation = generation,
                idFactory = SequentialRunIdFactory("desktop-${UUID.randomUUID()}"),
                clock = SystemRunClock,
                coroutineContext = Dispatchers.Default,
                connectionSettings = connectionSettings,
                conversationStore = conversationStore,
            )
            val presenter = ConversationPresenter(
                runs = runs,
                library = runs,
                scenarioSelection = scenarioSelection,
                clipboardWriter = clipboardWriter,
                coroutineContext = Dispatchers.Default,
                initialState = initialState,
            )
            val connectionSettingsPresenter = ConnectionSettingsPresenter(
                connectionSettings = connectionSettings,
                coroutineContext = Dispatchers.Default,
            )
            val runInspectorPresenter = RunInspectorPresenter(
                inspector = runs,
                reportGenerator = DefaultRunDiagnosticReportGenerator(runs),
                reportSaver = reportSaver,
                coroutineContext = Dispatchers.Default,
            )
            return DesktopRuntime(
                presenter,
                connectionSettingsPresenter,
                runInspectorPresenter,
                runs,
                closeHistoryResources,
                closeProviderResources,
            )
        }
    }
}
