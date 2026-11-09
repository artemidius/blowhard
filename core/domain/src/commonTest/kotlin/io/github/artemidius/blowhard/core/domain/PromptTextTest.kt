package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PromptTextTest {
    @Test
    fun `prompt preserves meaningful whitespace`() {
        val result = PromptText.from("  explain this  ")

        val accepted = assertIs<PromptTextResult.Accepted>(result)
        assertEquals("  explain this  ", accepted.prompt.value)
    }

    @Test
    fun `prompt rejects whitespace-only input`() {
        val result = PromptText.from(" \n\t")

        val rejected = assertIs<PromptTextResult.Rejected>(result)
        assertEquals(PromptRejection.BLANK, rejected.reason)
    }
}
