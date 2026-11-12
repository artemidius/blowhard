package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolProtocolTest {
    @Test
    fun `tool identities and JSON payloads are bounded`() {
        assertEquals("read_page", ToolName("read_page").value)
        assertFailsWith<IllegalArgumentException> { ToolName("ReadPage") }
        assertFailsWith<IllegalArgumentException> { ToolCallId("\n") }
        assertFailsWith<IllegalArgumentException> {
            ToolCallProposal(ToolCallId("call"), ToolName("read_page"), "x".repeat(MAX_TOOL_JSON_CHARACTERS + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            ToolCallResult(
                ToolCallId("call"),
                ToolName("read_page"),
                ToolCallOutcome.SUCCEEDED,
                "x".repeat(MAX_TOOL_OUTPUT_CHARACTERS + 1),
            )
        }
    }
}
