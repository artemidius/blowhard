package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class DocumentExtractionTest {
    @Test
    internal fun `extraction requires stable ordered source pages`() {
        val pages = listOf(ExtractedDocumentPage(0, "first"), ExtractedDocumentPage(1, ""))
        val extraction = DocumentExtraction(DocumentId("source"), "fixture-ocr-v1", 1, pages)

        assertEquals(pages, extraction.pages)
        assertFailsWith<IllegalArgumentException> {
            extraction.copy(pages = pages.reversed())
        }
        assertFailsWith<IllegalArgumentException> {
            extraction.copy(pages = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            extraction.copy(processor = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            extraction.copy(processedAtEpochMilliseconds = -1)
        }
    }

    @Test
    internal fun `page text may be empty but its source index cannot be negative`() {
        assertEquals("", ExtractedDocumentPage(0, "").markdown)
        assertFailsWith<IllegalArgumentException> { ExtractedDocumentPage(-1, "invalid") }
    }
}
