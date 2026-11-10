package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RunInspectionTest {
    @Test
    fun `token usage preserves unknown values and rejects empty or negative reports`() {
        TokenUsage(outputTokens = 0)

        assertFailsWith<IllegalArgumentException> { TokenUsage() }
        assertFailsWith<IllegalArgumentException> { TokenUsage(inputTokens = -1) }
    }

    @Test
    fun `inspection rejects empty oversized or nonchronological timelines`() {
        val accepted = RunInspectionEvent.Accepted(RunTimestamp(2))
        val started = RunInspectionEvent.Started(RunTimestamp(1))

        assertFailsWith<IllegalArgumentException> { inspection(emptyList()) }
        assertFailsWith<IllegalArgumentException> { inspection(List(RunInspection.MAX_EVENTS + 1) { accepted }) }
        assertFailsWith<IllegalArgumentException> { inspection(listOf(accepted, started)) }
    }

    private fun inspection(events: List<RunInspectionEvent>) = RunInspection(
        runId = RunId("run"),
        conversationId = ConversationId("conversation"),
        target = GenerationTarget.Demo,
        parameters = GenerationParameters(),
        events = events,
    )
}
