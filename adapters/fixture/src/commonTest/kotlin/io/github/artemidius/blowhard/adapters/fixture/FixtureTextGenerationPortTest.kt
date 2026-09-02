package io.github.artemidius.blowhard.adapters.fixture

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.PromptText
import io.github.artemidius.blowhard.core.domain.PromptTextResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FixtureTextGenerationPortTest {
    @Test
    fun `success streams configured chunks and completion`() = runTest {
        val events = events(FixtureScenario.Success(listOf("Hello", " world")))

        assertEquals(
            listOf(
                GenerationEvent.TextDelta("Hello"),
                GenerationEvent.TextDelta(" world"),
                GenerationEvent.Completed,
            ),
            events,
        )
    }

    @Test
    fun `classified failure retains safe fixture details`() = runTest {
        val events = events(
            FixtureScenario.Failure(
                chunks = listOf("partial"),
                kind = GenerationFailureKind.UNAVAILABLE,
                reference = "fixture-unavailable",
            ),
        )

        val failure = assertIs<GenerationEvent.Failed>(events.last()).failure
        assertEquals(GenerationFailureKind.UNAVAILABLE, failure.kind)
        assertEquals("fixture-unavailable", failure.reference)
    }

    @Test
    fun `rate limit has its own deterministic scenario`() = runTest {
        val failure = assertIs<GenerationEvent.Failed>(events(FixtureScenario.RateLimited()).single()).failure

        assertEquals(GenerationFailureKind.RATE_LIMITED, failure.kind)
    }

    @Test
    fun `interruption ends with a synthetic transport exception`() = runTest {
        assertFailsWith<RuntimeException> {
            events(FixtureScenario.Interrupted(listOf("partial")))
        }
    }

    @Test
    fun `chunk timing uses virtual coroutine time`() = runTest {
        val deferred = async {
            events(FixtureScenario.Success(listOf("one", "two"), 2.seconds))
        }
        runCurrent()
        assertEquals(false, deferred.isCompleted)

        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(true, deferred.isCompleted)
    }

    @Test
    fun `all fixture scenarios reject negative delays`() {
        assertFailsWith<IllegalArgumentException> { FixtureScenario.Success(emptyList(), (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { FixtureScenario.Failure(delayBetweenChunks = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { FixtureScenario.RateLimited(delayBetweenChunks = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { FixtureScenario.Interrupted(delayBetweenChunks = (-1).seconds) }
    }

    private suspend fun events(scenario: FixtureScenario): List<GenerationEvent> =
        FixtureTextGenerationPort { scenario }.generate(request()).toList()

    private fun request(): GenerationRequest {
        val prompt = assertIs<PromptTextResult.Accepted>(PromptText.from("Hello")).prompt
        return GenerationRequest(RunId("run-1"), ConversationId("conversation-1"), prompt)
    }
}
