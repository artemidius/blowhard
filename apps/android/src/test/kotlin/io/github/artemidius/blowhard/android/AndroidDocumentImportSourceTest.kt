package io.github.artemidius.blowhard.android

import io.github.artemidius.blowhard.core.api.DocumentReadResult
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AndroidDocumentImportSourceTest {
    @Test
    fun `source returns an isolated bounded copy`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val source = AndroidDocumentImportSource.testSource("scan.png", bytes.size.toLong()) {
            ByteArrayInputStream(bytes)
        }

        val result = source.read(3) as DocumentReadResult.Content
        bytes[0] = 9

        assertEquals("scan.png", source.displayName)
        assertEquals(3L, source.declaredSizeBytes)
        assertContentEquals(byteArrayOf(1, 2, 3), result.copyBytes())
    }

    @Test
    fun `source rejects overflow and unavailable streams`() = runTest {
        val tooLarge = AndroidDocumentImportSource.testSource("scan.pdf", null) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        val unavailable = AndroidDocumentImportSource.testSource("scan.pdf", null) { null }

        assertEquals(DocumentReadResult.TooLarge, tooLarge.read(2))
        assertEquals(DocumentReadResult.Unavailable, unavailable.read(2))
    }
}
