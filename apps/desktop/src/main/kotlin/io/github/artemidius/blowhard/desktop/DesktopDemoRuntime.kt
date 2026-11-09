package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureTextGenerationPort
import io.github.artemidius.blowhard.core.application.DefaultConversationRuns
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.core.application.SystemRunClock
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopDemoRuntime private constructor(
    val presenter: ConversationPresenter,
    private val runs: DefaultConversationRuns,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        presenter.close()
        runBlocking { runs.close() }
    }

    companion object {
        fun create(): DesktopDemoRuntime {
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
                coroutineContext = Dispatchers.Default,
            )
            return DesktopDemoRuntime(presenter, runs)
        }
    }
}
