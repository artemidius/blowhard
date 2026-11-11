package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.DocumentReadResult
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class PathDocumentImportSourceTest {
    @Test
    internal fun selectedPathIsReducedToMetadataAndBoundedCopiedContent() = runTest {
        val path = Files.createTempFile("blowhard-source-", ".pdf")
        val expected = "local bytes".encodeToByteArray()
        try {
            Files.write(path, expected)
            val source = PathDocumentImportSource(path)

            assertEquals(path.fileName.toString(), source.displayName)
            assertEquals(expected.size.toLong(), source.declaredSizeBytes)
            val result = assertIs<DocumentReadResult.Content>(source.read(expected.size.toLong()))
            val first = result.copyBytes()
            first[0] = 'x'.code.toByte()
            assertContentEquals(expected, result.copyBytes())
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    internal fun oversizedMissingAndInvalidReadsFailClosed() = runTest {
        val path = Files.createTempFile("blowhard-source-", ".png")
        try {
            Files.write(path, "oversized".encodeToByteArray())
            val source = PathDocumentImportSource(path)
            assertEquals(DocumentReadResult.TooLarge, source.read(1))
            assertEquals(DocumentReadResult.Unavailable, source.read(0))
            path.deleteIfExists()
            assertEquals(DocumentReadResult.Unavailable, source.read(1))
        } finally {
            path.deleteIfExists()
        }
    }
}
