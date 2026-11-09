package io.github.artemidius.blowhard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafeMarkdownTest {
    @Test
    fun `parser supports only headings paragraphs bullets and fenced code`() {
        val blocks = parseSafeMarkdown(
            """
            # Title

            A paragraph
            on two lines.

            - first
            * second

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertEquals(5, blocks.size)
        assertEquals(SafeMarkdownBlock.Heading(1, "Title"), blocks[0])
        assertEquals(SafeMarkdownBlock.Paragraph("A paragraph on two lines."), blocks[1])
        assertEquals(SafeMarkdownBlock.Bullet("first"), blocks[2])
        assertEquals(SafeMarkdownBlock.Bullet("second"), blocks[3])
        assertEquals(SafeMarkdownBlock.Code("val answer = 42"), blocks[4])
    }

    @Test
    fun `html and markdown links remain inert text`() {
        val input = "<script>danger()</script> [open](https://example.invalid)"
        val block = parseSafeMarkdown(input).single()

        assertEquals(SafeMarkdownBlock.Paragraph(input), block)
    }

    @Test
    fun `unfinished code fence is retained as code`() {
        val block = parseSafeMarkdown("```\nvalue").single()

        assertEquals(SafeMarkdownBlock.Code("value"), block)
    }

    @Test
    fun `blank input produces no render nodes`() {
        assertTrue(parseSafeMarkdown("  \n").isEmpty())
    }

    @Test
    fun `three heading levels are classified`() {
        val blocks = parseSafeMarkdown("# one\n## two\n### three")

        assertEquals(listOf(1, 2, 3), blocks.map { assertIs<SafeMarkdownBlock.Heading>(it).level })
    }
}
