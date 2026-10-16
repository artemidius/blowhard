package io.github.artemidius.blowhard.adapters.fixture

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class FixtureDocumentOcrPortTest {
    @Test
    internal fun `fixture preserves source page count and emits deterministic markdown tables`() = runTest {
        val document = ImportedDocument(
            id = DocumentId("source"),
            displayName = "review.pdf",
            sizeBytes = 1,
            importedAtEpochMilliseconds = 1,
            content = DocumentContentDescriptor.Pdf(pageCount = 2),
        )

        val result = assertIs<DocumentOcrPortResult.Processed>(
            FixtureDocumentOcrPort().process(DocumentOcrRequest(document, byteArrayOf(1))),
        )

        assertEquals("fixture-ocr-v1", result.processor)
        assertEquals(listOf(0, 1), result.pages.map { it.index })
        assertTrue(result.pages.all { it.markdown.contains("| Field | Value |") })
        assertTrue(result.pages[1].markdown.contains("page 2 of 2"))
    }

    @Test
    internal fun `image fixture emits exactly one page`() = runTest {
        val document = ImportedDocument(
            id = DocumentId("image"),
            displayName = "scan.png",
            sizeBytes = 1,
            importedAtEpochMilliseconds = 1,
            content = DocumentContentDescriptor.Image(DocumentFormat.PNG, 10, 20),
        )

        val result = assertIs<DocumentOcrPortResult.Processed>(
            FixtureDocumentOcrPort().process(DocumentOcrRequest(document, byteArrayOf(1))),
        )

        assertEquals(1, result.pages.size)
        assertTrue(result.pages.single().markdown.contains("scan.png"))
    }
}
