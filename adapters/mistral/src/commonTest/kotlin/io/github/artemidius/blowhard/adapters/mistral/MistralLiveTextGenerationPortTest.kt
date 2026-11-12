package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.PromptTextResult
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MistralLiveTextGenerationPortTest {
    @Test
    fun `live attempt uses immutable model target and closes its session`() = runTest {
        var observedModel: String? = null
        var closeCalls = 0
        val port = MistralLiveTextGenerationPort.createForTest { modelId ->
            observedModel = modelId
            MistralGenerationSession(
                generation = TextGenerationPort { flowOf(GenerationEvent.Completed) },
                close = { closeCalls += 1 },
            )
        }

        val events = port.generate(request(GenerationTarget.Live("verified-model"))).toList()

        assertEquals("verified-model", observedModel)
        assertEquals(listOf(GenerationEvent.Completed), events)
        assertEquals(1, closeCalls)
    }

    @Test
    fun `demo attempt is rejected without creating a provider session`() = runTest {
        var factoryCalls = 0
        val port = MistralLiveTextGenerationPort.createForTest {
            factoryCalls += 1
            error("must not create a live session")
        }

        val failure = assertIs<GenerationEvent.Failed>(port.generate(request(GenerationTarget.Demo)).toList().single())

        assertEquals(GenerationFailureKind.INVALID_REQUEST, failure.failure.kind)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `session construction failure is redacted`() = runTest {
        val port = MistralLiveTextGenerationPort.createForTest {
            error("private provider construction detail")
        }

        val failure = assertIs<GenerationEvent.Failed>(
            port.generate(request(GenerationTarget.Live("verified-model"))).toList().single(),
        )

        assertEquals(GenerationFailureKind.UNEXPECTED, failure.failure.kind)
    }

    @Test
    fun `cancellation propagates after closing the active session`() = runTest {
        var closeCalls = 0
        val port = MistralLiveTextGenerationPort.createForTest {
            MistralGenerationSession(
                generation = TextGenerationPort { flow { throw CancellationException("cancelled") } },
                close = { closeCalls += 1 },
            )
        }

        assertFailsWith<CancellationException> {
            port.generate(request(GenerationTarget.Live("verified-model"))).toList()
        }
        assertEquals(1, closeCalls)
    }

    @Test
    fun `cleanup failure does not replace provider terminal event`() = runTest {
        val port = MistralLiveTextGenerationPort.createForTest {
            MistralGenerationSession(
                generation = TextGenerationPort { flowOf(GenerationEvent.Completed) },
                close = { error("private cleanup detail") },
            )
        }

        assertEquals(
            listOf(GenerationEvent.Completed),
            port.generate(request(GenerationTarget.Live("verified-model"))).toList(),
        )
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
