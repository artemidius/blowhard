package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenarioSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopRuntime private constructor(
    val presenter: ConversationPresenter,
    val connectionSettingsPresenter: ConnectionSettingsPresenter,
    private val runs: DefaultConversationRuns,
    private val closeHistoryResources: () -> Unit,
    private val closeProviderResources: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
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
            initialState: ConversationUiState = emptyDesktopConversationState(),
            closeHistoryResources: () -> Unit = {},
            closeProviderResources: () -> Unit = {},
        ): DesktopRuntime {
            val runs = DefaultConversationRuns(
                generation = generation,
                idFactory = SequentialRunIdFactory("desktop-${UUID.randomUUID()}"),
                clock = SystemRunClock,
                coroutineContext = Dispatchers.Default,
                connectionSettings = connectionSettings,
            )
            val presenter = ConversationPresenter(
                runs = runs,
                scenarioSelection = scenarioSelection,
                clipboardWriter = clipboardWriter,
                coroutineContext = Dispatchers.Default,
                initialState = initialState,
            )
            val connectionSettingsPresenter = ConnectionSettingsPresenter(
                connectionSettings = connectionSettings,
                coroutineContext = Dispatchers.Default,
            )
            return DesktopRuntime(
                presenter,
                connectionSettingsPresenter,
                runs,
                closeHistoryResources,
                closeProviderResources,
            )
        }
    }
}
