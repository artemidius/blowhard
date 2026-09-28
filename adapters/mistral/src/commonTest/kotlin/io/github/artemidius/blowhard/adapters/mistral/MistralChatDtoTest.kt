package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
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
        val usage = buildJsonObject {}

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
        )
        val requestWithControls = requestWithoutLimit.copy(maxTokens = 1, temperature = 0.2)

        assertNull(deltaAbsent.content)
        assertNull(choiceAbsent.finishReason)
        assertTrue(chunks.first().choices.isEmpty())
        assertEquals(usage, chunks.last().usage)
        assertNull(requestWithoutLimit.maxTokens)
        assertNull(requestWithoutLimit.temperature)
        assertEquals(1, requestWithControls.maxTokens)
        assertEquals(0.2, requestWithControls.temperature)
    }
}
