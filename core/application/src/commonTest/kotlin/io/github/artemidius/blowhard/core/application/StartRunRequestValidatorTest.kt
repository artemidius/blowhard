package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StartRunRequestValidatorTest {
    private val validator = StartRunRequestValidator()
    private val conversationId = ConversationId("conversation-01")

    @Test
    fun `accepts a meaningful prompt without rewriting it`() {
        val result = validator.validate(StartRunRequest(conversationId, "  Hello  "))

        val accepted = assertIs<StartRunValidation.Accepted>(result)
        assertEquals("  Hello  ", accepted.prompt.value)
    }

    @Test
    fun `maps blank prompt to a client-safe rejection`() {
        val result = validator.validate(StartRunRequest(conversationId, " \n "))

        val rejected = assertIs<StartRunValidation.Rejected>(result)
        assertEquals(StartRunRejection.BLANK_PROMPT, rejected.reason)
    }
}
