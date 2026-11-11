package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MeetingNoteGenerationPortTest {
    @Test
    fun `request keeps sensitive source out of its string representation`() {
        val request = validRequest(sourceJson = "private transcript")

        assertEquals("private transcript", request.sourceJson)
        assertFalse(request.toString().contains("private transcript"))
    }

    @Test
    fun `request rejects blank and unbounded fields`() {
        assertFailsWith<IllegalArgumentException> { validRequest(instructions = " ") }
        assertFailsWith<IllegalArgumentException> {
            validRequest(
                instructions = "x".repeat(MeetingNoteGenerationRequest.MAX_INSTRUCTION_CHARACTERS + 1),
            )
        }
        assertFailsWith<IllegalArgumentException> { validRequest(sourceJson = " ") }
        assertFailsWith<IllegalArgumentException> {
            validRequest(sourceJson = "x".repeat(MeetingNoteGenerationRequest.MAX_SOURCE_CHARACTERS + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            validRequest(schemaName = "x".repeat(MeetingNoteGenerationRequest.MAX_SCHEMA_NAME_CHARACTERS + 1))
        }
        assertFailsWith<IllegalArgumentException> { validRequest(schemaJson = " ") }
        assertFailsWith<IllegalArgumentException> {
            validRequest(schemaJson = "x".repeat(MeetingNoteGenerationRequest.MAX_SCHEMA_CHARACTERS + 1))
        }
        assertFailsWith<IllegalArgumentException> { validRequest(maximumOutputTokens = 0) }
        assertFailsWith<IllegalArgumentException> {
            validRequest(maximumOutputTokens = MeetingNoteGenerationRequest.MAX_OUTPUT_TOKENS + 1)
        }
    }

    private fun validRequest(
        instructions: String = "Extract JSON",
        sourceJson: String = "{}",
        schemaName: String = "note_v1",
        schemaJson: String = "{}",
        maximumOutputTokens: Int = 1,
    ): MeetingNoteGenerationRequest = MeetingNoteGenerationRequest(
        instructions = instructions,
        sourceJson = sourceJson,
        schemaName = schemaName,
        schemaJson = schemaJson,
        maximumOutputTokens = maximumOutputTokens,
    )
}
