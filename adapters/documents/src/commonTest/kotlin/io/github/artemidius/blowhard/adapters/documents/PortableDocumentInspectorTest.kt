package io.github.artemidius.blowhard.adapters.documents

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PortableDocumentInspectorTest {
    @Test
    fun `identifies a structurally valid PNG and verifies its checksum`() = runTest {
        assertEquals(
            DocumentContentDescriptor.Image(DocumentFormat.PNG, IMAGE_WIDTH, IMAGE_HEIGHT),
            inspect(PNG),
        )

        val corrupted = PNG.copyOf().also { bytes ->
            val dataIndex = bytes.indexOf('x'.code.toByte())
            bytes[dataIndex] = (bytes[dataIndex].toInt() xor 1).toByte()
        }
        assertIs<DocumentInspectionResult.InvalidContent>(PortableDocumentInspector.inspect(corrupted))
    }

    @Test
    fun `identifies a JPEG only after a frame and scan header`() = runTest {
        assertEquals(
            DocumentContentDescriptor.Image(DocumentFormat.JPEG, IMAGE_WIDTH, IMAGE_HEIGHT),
            inspect(JPEG),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(PortableDocumentInspector.inspect(JPEG.copyOf(JPEG.size - 2)))
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(hex(JPEG_WITHOUT_SCAN)),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(PortableDocumentInspector.inspect(hex("ffd8ffd9")))
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(hex("ffd80000000000000000ffd9")),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(hex("ffd8ffc00001ffd9")),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(hex(JPEG_WITH_INVALID_SCAN)),
        )
    }

    @Test
    fun `counts PDF page objects without counting the page tree`() = runTest {
        val pdf = """
            %PDF-1.7
            1 0 obj << /Type /Pages /Count 2 >> endobj
            2 0 obj << /Type /Page /Parent 1 0 R >> endobj
            3 0 obj << /Type/Page /Parent 1 0 R >> endobj
            %%EOF
        """.trimIndent().encodeToByteArray()

        assertEquals(DocumentContentDescriptor.Pdf(PDF_PAGE_COUNT), inspect(pdf))
        assertEquals(
            DocumentContentDescriptor.Pdf(1),
            inspect("%PDF-2.0\n1 0 obj << /Type/Page >> endobj\n%%EOF \n".encodeToByteArray()),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect("%PDF-1.7\n1 0 obj << /Type /Pages >>\n%%EOF".encodeToByteArray()),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect("%PDF-3.0\n%%EOF".encodeToByteArray()),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(
                "%PDF-1.7\n1 0 obj << /Type /ObjStm /Type /Page >> endobj\n%%EOF".encodeToByteArray(),
            ),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect(
                "%PDF-1.7\ntrailer << /Encrypt 4 0 R /Type /Page >>\n%%EOF".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun `distinguishes unsupported bytes from malformed supported content`() = runTest {
        assertIs<DocumentInspectionResult.UnsupportedFormat>(
            PortableDocumentInspector.inspect("plain text".encodeToByteArray()),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(
            PortableDocumentInspector.inspect("%PDF-1.7\nmissing eof".encodeToByteArray()),
        )
        assertIs<DocumentInspectionResult.InvalidContent>(PortableDocumentInspector.inspect(PNG.copyOf(PNG.size / 2)))
    }

    @Test
    fun `binary helpers reject unsafe bounds and distinguish PDF delimiters`() {
        val content = byteArrayOf('A'.code.toByte())
        assertFalse(content.matchesBytes(-1, intArrayOf('A'.code)))
        assertFalse(content.matchesBytes(content.size, intArrayOf('A'.code)))
        assertFalse(content.matchesBytes(Int.MAX_VALUE, intArrayOf('A'.code)))
        assertTrue(0.isPdfWhitespace())
        assertTrue('/'.code.isPdfDelimiter())
        assertFalse('A'.code.isPdfDelimiter())
    }

    private suspend fun inspect(content: ByteArray): DocumentContentDescriptor =
        assertIs<DocumentInspectionResult.Inspected>(PortableDocumentInspector.inspect(content)).descriptor

    private companion object {
        const val IMAGE_WIDTH = 2
        const val IMAGE_HEIGHT = 3
        const val PDF_PAGE_COUNT = 2
        val PNG = hex(
            "89504e470d0a1a0a0000000d4948445200000002000000030806000000b9eade81" +
                "0000000b49444154789c636080020000090001fb52b8a90000000049454e44ae426082",
        )
        const val JPEG_WITHOUT_SCAN =
            "ffd8ffe000104a46494600010100000100010000ffc00011080003000203011100021100031100ffd9"
        const val JPEG_WITH_INVALID_SCAN =
            "ffd8ffc00011080003000203011100021100031100ffda00020000ffd9"
        val JPEG = hex(
            "ffd8ffe000104a46494600010100000100010000ffc00011080003000203011100021100031100" +
                "ffda000c03010002000300003f00ffd9",
        )
    }
}

private fun hex(value: String): ByteArray {
    require(value.length % 2 == 0)
    return ByteArray(value.length / 2) { index ->
        val offset = index * 2
        value.substring(offset, offset + 2).toInt(radix = 16).toByte()
    }
}
