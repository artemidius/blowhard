package io.github.artemidius.blowhard.ui

internal sealed interface SafeMarkdownBlock {
    data class Heading(val level: Int, val text: String) : SafeMarkdownBlock

    data class Paragraph(val text: String) : SafeMarkdownBlock

    data class Bullet(val text: String) : SafeMarkdownBlock

    data class Code(val text: String) : SafeMarkdownBlock
}

internal fun parseSafeMarkdown(input: String): List<SafeMarkdownBlock> = SafeMarkdownParser(input).parse()

private class SafeMarkdownParser(private val input: String) {
    private val blocks = mutableListOf<SafeMarkdownBlock>()
    private val code = mutableListOf<String>()
    private val paragraph = mutableListOf<String>()
    private var insideCode = false

    fun parse(): List<SafeMarkdownBlock> {
        if (input.isBlank()) return emptyList()
        input.lines().forEach(::acceptLine)
        if (insideCode && code.isNotEmpty()) flushCode()
        flushParagraph()
        return blocks
    }

    private fun acceptLine(rawLine: String) {
        val trimmed = rawLine.trim()
        when {
            trimmed.startsWith(CODE_FENCE) -> toggleCode()
            insideCode -> code += rawLine
            trimmed.isEmpty() -> flushParagraph()
            else -> acceptText(trimmed)
        }
    }

    private fun toggleCode() {
        if (insideCode) flushCode() else flushParagraph()
        insideCode = !insideCode
    }

    private fun acceptText(text: String) {
        val heading = heading(text)
        when {
            heading != null -> {
                flushParagraph()
                blocks += heading
            }

            text.startsWith("- ") || text.startsWith("* ") -> {
                flushParagraph()
                blocks += SafeMarkdownBlock.Bullet(text.drop(BULLET_PREFIX_LENGTH))
            }

            else -> paragraph += text
        }
    }

    private fun heading(text: String): SafeMarkdownBlock.Heading? = when {
        text.startsWith("### ") -> SafeMarkdownBlock.Heading(HEADING_LEVEL_THREE, text.removePrefix("### "))
        text.startsWith("## ") -> SafeMarkdownBlock.Heading(HEADING_LEVEL_TWO, text.removePrefix("## "))
        text.startsWith("# ") -> SafeMarkdownBlock.Heading(HEADING_LEVEL_ONE, text.removePrefix("# "))
        else -> null
    }

    private fun flushCode() {
        blocks += SafeMarkdownBlock.Code(code.joinToString("\n"))
        code.clear()
    }

    private fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += SafeMarkdownBlock.Paragraph(paragraph.joinToString(" "))
        paragraph.clear()
    }
}

private const val CODE_FENCE = "```"
private const val BULLET_PREFIX_LENGTH = 2
private const val HEADING_LEVEL_ONE = 1
private const val HEADING_LEVEL_TWO = 2
private const val HEADING_LEVEL_THREE = 3
