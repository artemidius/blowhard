package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

internal class DocumentOcrContractTest {
    @Test
    internal fun `processor request and stored source isolate mutable bytes`() {
        val input = byteArrayOf(1, 2, 3)
        val request = DocumentOcrRequest(document(), input)
        val source = StoredDocumentSource(document(), input)
        input[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), request.copyContent())
        assertContentEquals(byteArrayOf(1, 2, 3), source.copyContent())
        request.copyContent()[0] = 8
        source.copyContent()[0] = 7
        assertContentEquals(byteArrayOf(1, 2, 3), request.copyContent())
        assertContentEquals(byteArrayOf(1, 2, 3), source.copyContent())
    }

    @Test
    internal fun `processor input rejects inconsistent metadata`() {
        assertFailsWith<IllegalArgumentException> { DocumentOcrRequest(document(), byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { StoredDocumentSource(document(), byteArrayOf(1)) }
    }

    private fun document(): ImportedDocument = ImportedDocument(
        id = DocumentId("document"),
        displayName = "meeting.pdf",
        sizeBytes = 3,
        importedAtEpochMilliseconds = 1,
        content = DocumentContentDescriptor.Pdf(2),
    )
}
