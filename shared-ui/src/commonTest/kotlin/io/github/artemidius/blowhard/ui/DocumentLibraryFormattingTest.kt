package io.github.artemidius.blowhard.ui

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class DocumentLibraryFormattingTest {
    @Test
    internal fun `verified metadata has concise platform-neutral labels`() {
        assertEquals("PDF · 1 page", DocumentContentDescriptor.Pdf(1).description())
        assertEquals("PDF · 2 pages", DocumentContentDescriptor.Pdf(2).description())
        assertEquals(
            "PNG · 640 × 480 px",
            DocumentContentDescriptor.Image(DocumentFormat.PNG, 640, 480).description(),
        )
        assertEquals("42 B", 42L.readableByteCount())
        assertEquals("2 KB", 2_048L.readableByteCount())
        assertEquals("3 MB", (3L * 1024L * 1024L).readableByteCount())
    }

    @Test
    internal fun `rejection feedback exposes policy without source details`() {
        val message = DocumentLibraryNotice.ImportRejected(DocumentImportRejection.TOO_LARGE).message()

        assertEquals("The selected document exceeds the 20 MB local import limit.", message)
        assertFalse(message.contains("/"))
    }
}
