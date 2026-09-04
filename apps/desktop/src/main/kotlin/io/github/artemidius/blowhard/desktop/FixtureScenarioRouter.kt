package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureScenario
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.ui.presentation.DemoScenario
import io.github.artemidius.blowhard.ui.presentation.DemoScenarioSelection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

internal class FixtureScenarioRouter : DemoScenarioSelection {
    private val selections = ConcurrentHashMap<String, DemoScenario>()

    override fun prepare(conversationId: ConversationId, scenario: DemoScenario) {
        selections[conversationId.value] = scenario
    }

    fun scenarioFor(request: GenerationRequest): FixtureScenario = when (
        selections.remove(request.conversationId.value) ?: DemoScenario.SUCCESS
    ) {
        DemoScenario.SUCCESS -> FixtureScenario.Success(
            chunks = listOf(
                "# Offline demonstration\n\n",
                "This response is streamed through the same shared run API planned for a live provider.\n\n",
                "- deterministic fixture\n",
                "- passive observation\n",
                "- cancellable execution\n\n```kotlin\nConversationRuns.start(request)\n```",
            ),
            delayBetweenChunks = CHUNK_DELAY,
        )

        DemoScenario.FAILURE -> FixtureScenario.Failure(
            chunks = listOf("Partial output was preserved before the synthetic failure."),
            delayBetweenChunks = CHUNK_DELAY,
        )

        DemoScenario.RATE_LIMITED -> FixtureScenario.RateLimited(reference = "fixture-rate-limit")

        DemoScenario.INTERRUPTED -> FixtureScenario.Interrupted(
            chunks = listOf("The synthetic stream started, then its transport disappeared."),
            delayBetweenChunks = CHUNK_DELAY,
        )
    }
}

private val CHUNK_DELAY = 350.milliseconds
