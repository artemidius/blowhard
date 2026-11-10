package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.TokenUsage
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MistralChatDtoTest {
    @Test
    fun `optional wire fields preserve absence across constructor shapes`() {
        val deltaAbsent = MistralChatDeltaDto()
        val deltaExplicit = MistralChatDeltaDto(JsonNull)
        val choiceAbsent = MistralChatChoiceDto(0, deltaAbsent)
        val choiceExplicit = MistralChatChoiceDto(0, deltaExplicit, finishReason = null)
        val usage = MistralUsageDto(completionTokens = 2)

        val chunks = listOf(
            MistralChatChunkDto(),
            MistralChatChunkDto(choices = listOf(choiceAbsent)),
            MistralChatChunkDto(usage = usage),
            MistralChatChunkDto(listOf(choiceExplicit), usage),
        )
        val requestWithoutLimit = MistralChatRequestDto(
            model = "model",
            messages = listOf(MistralChatMessageDto("user", "prompt")),
            stream = true,
            streamOptions = MistralStreamOptionsDto(includeUsage = true),
        )
        val requestWithControls = requestWithoutLimit.copy(maxTokens = 1, temperature = 0.2)

        assertNull(deltaAbsent.content)
        assertNull(choiceAbsent.finishReason)
        assertTrue(chunks.first().choices.isEmpty())
        assertEquals(usage, chunks.last().usage)
        assertEquals(TokenUsage(outputTokens = 2), usage.toTokenUsage())
        assertNull(MistralUsageDto().toTokenUsage())
        assertTrue(requestWithoutLimit.streamOptions.includeUsage)
        assertNull(requestWithoutLimit.maxTokens)
        assertNull(requestWithoutLimit.temperature)
        assertEquals(1, requestWithControls.maxTokens)
        assertEquals(0.2, requestWithControls.temperature)
    }
}
