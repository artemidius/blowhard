package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportedDocumentTest {
    @Test
    fun `verified PDF metadata preserves bounded characteristics`() {
        val document = ImportedDocument(
            id = DocumentId("document-1"),
            displayName = "notes.pdf",
            sizeBytes = 42,
            importedAtEpochMilliseconds = 7,
            content = DocumentContentDescriptor.Pdf(pageCount = 3),
        )

        assertEquals(DocumentFormat.PDF, document.content.format)
    }

    @Test
    fun `image descriptor accepts only positive raster dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentContentDescriptor.Image(DocumentFormat.PDF, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentContentDescriptor.Image(DocumentFormat.PNG, 0, 1)
        }
    }

    @Test
    fun `document metadata rejects invalid local values`() {
        val descriptor = DocumentContentDescriptor.Pdf(1)
        assertFailsWith<IllegalArgumentException> {
            ImportedDocument(DocumentId("id"), " ", 1, 0, descriptor)
        }
        assertFailsWith<IllegalArgumentException> {
            ImportedDocument(DocumentId("id"), "valid.pdf", 0, 0, descriptor)
        }
        assertFailsWith<IllegalArgumentException> {
            ImportedDocument(DocumentId("id"), "valid.pdf", 1, -1, descriptor)
        }
    }
}
