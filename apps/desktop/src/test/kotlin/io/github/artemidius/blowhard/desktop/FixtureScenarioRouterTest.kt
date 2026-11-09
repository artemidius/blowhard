package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.fixture.FixtureScenario
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.PromptText
import io.github.artemidius.blowhard.core.domain.PromptTextResult
import io.github.artemidius.blowhard.ui.presentation.DemoScenario
import kotlin.test.Test
import kotlin.test.assertIs

class FixtureScenarioRouterTest {
    @Test
    fun `scenario selections are isolated by conversation and consumed once`() {
        val router = FixtureScenarioRouter()
        val first = ConversationId("first")
        val second = ConversationId("second")
        router.prepare(first, DemoScenario.RATE_LIMITED)
        router.prepare(second, DemoScenario.INTERRUPTED)

        assertIs<FixtureScenario.Interrupted>(router.scenarioFor(request(second)))
        assertIs<FixtureScenario.RateLimited>(router.scenarioFor(request(first)))
        assertIs<FixtureScenario.Success>(router.scenarioFor(request(first)))
    }

    @Test
    fun `all explicit demo choices map to their fixture behavior`() {
        val router = FixtureScenarioRouter()
        DemoScenario.entries.forEach { scenario ->
            val conversation = ConversationId(scenario.name)
            router.prepare(conversation, scenario)
            val fixture = router.scenarioFor(request(conversation))
            when (scenario) {
                DemoScenario.SUCCESS -> assertIs<FixtureScenario.Success>(fixture)
                DemoScenario.FAILURE -> assertIs<FixtureScenario.Failure>(fixture)
                DemoScenario.RATE_LIMITED -> assertIs<FixtureScenario.RateLimited>(fixture)
                DemoScenario.INTERRUPTED -> assertIs<FixtureScenario.Interrupted>(fixture)
            }
        }
    }

    private fun request(conversationId: ConversationId): GenerationRequest {
        val prompt = assertIs<PromptTextResult.Accepted>(PromptText.from("demo")).prompt
        return GenerationRequest(RunId("run-${conversationId.value}"), conversationId, prompt)
    }
}
