package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.PromptTextResult
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopGenerationRouterTest {
    @Test
    fun `demo and live attempts route only to their immutable target`() = runTest {
        var demoCalls = 0
        var liveCalls = 0
        val liveFailure = GenerationEvent.Failed(GenerationFailure(GenerationFailureKind.RATE_LIMITED))
        val router = DesktopGenerationRouter(
            demo = TextGenerationPort {
                demoCalls += 1
                flowOf(GenerationEvent.Completed)
            },
            liveFactory = { modelId ->
                assertEquals("verified-model", modelId)
                OwnedGenerationPort(
                    TextGenerationPort {
                        liveCalls += 1
                        flowOf(liveFailure)
                    },
                ) {}
            },
        )

        assertEquals(listOf(GenerationEvent.Completed), router.generate(request(GenerationTarget.Demo)).toList())
        assertEquals(listOf(liveFailure), router.generate(request(GenerationTarget.Live("verified-model"))).toList())

        assertEquals(1, demoCalls)
        assertEquals(1, liveCalls)
        router.close()
    }

    @Test
    fun `live factory failure is redacted and never falls back to demo`() = runTest {
        var demoCalls = 0
        val router = DesktopGenerationRouter(
            demo = TextGenerationPort {
                demoCalls += 1
                flowOf(GenerationEvent.Completed)
            },
            liveFactory = { error("private provider construction detail") },
        )

        val events = router.generate(request(GenerationTarget.Live("verified-model"))).toList()

        assertEquals(0, demoCalls)
        assertEquals(GenerationFailureKind.UNEXPECTED, assertIs<GenerationEvent.Failed>(events.single()).failure.kind)
    }

    @Test
    fun `one live port per model is reused and closed exactly once`() = runTest {
        var factoryCalls = 0
        var closeCalls = 0
        val router = DesktopGenerationRouter(
            demo = TextGenerationPort { flowOf(GenerationEvent.Completed) },
            liveFactory = {
                factoryCalls += 1
                OwnedGenerationPort(
                    TextGenerationPort { flowOf(GenerationEvent.Completed) },
                ) { closeCalls += 1 }
            },
        )
        val request = request(GenerationTarget.Live("verified-model"))

        router.generate(request).toList()
        router.generate(request).toList()
        router.close()
        router.close()

        assertEquals(1, factoryCalls)
        assertEquals(1, closeCalls)
    }
}

private fun request(target: GenerationTarget): GenerationRequest = GenerationRequest(
    runId = RunId("run"),
    conversationId = ConversationId("conversation"),
    prompt = assertIs<PromptTextResult.Accepted>(
        io.github.artemidius.blowhard.core.domain.PromptText.from("Hello"),
    ).prompt,
    target = target,
)
