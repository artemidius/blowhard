package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureTextGenerationPort
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopDemoRuntime private constructor(
    val presenter: ConversationPresenter,
    val connectionSettingsPresenter: ConnectionSettingsPresenter,
    private val runs: DefaultConversationRuns,
    private val closeProviderResources: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connectionSettingsPresenter.close()
        presenter.close()
        runBlocking { runs.close() }
        closeProviderResources()
    }

    companion object {
        fun create(
            clipboardWriter: ClipboardWriter,
            connectionSettings: ConnectionSettings,
            closeProviderResources: () -> Unit = {},
        ): DesktopDemoRuntime {
            val scenarios = FixtureScenarioRouter()
            val runs = DefaultConversationRuns(
                generation = FixtureTextGenerationPort(scenarios::scenarioFor),
                idFactory = SequentialRunIdFactory("desktop-run"),
                clock = SystemRunClock,
                coroutineContext = Dispatchers.Default,
            )
            val presenter = ConversationPresenter(
                runs = runs,
                scenarioSelection = scenarios,
                clipboardWriter = clipboardWriter,
                coroutineContext = Dispatchers.Default,
            )
            val connectionSettingsPresenter = ConnectionSettingsPresenter(
                connectionSettings = connectionSettings,
                coroutineContext = Dispatchers.Default,
            )
            return DesktopDemoRuntime(presenter, connectionSettingsPresenter, runs, closeProviderResources)
        }
    }
}
