package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.persistence.NativeLocalDatabaseFiles
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Native iOS SQLite evidence for the document session. */
internal class IosDocumentSessionTest {
    @Test
    internal fun importedDocumentSurvivesSessionRestart() = runTest {
        val databaseName = "documents-${NSUUID().UUIDString}.db"
        val source = IosByteDocumentSource("scan.png", PNG)

        try {
            val first = requireNotNull(IosDocumentSession.openOrNull(databaseName))
            assertIs<DocumentImportResult.Imported>(first.library.import(source))
            first.close()

            val second = requireNotNull(IosDocumentSession.openOrNull(databaseName))
            val stored = assertIs<DocumentListResult.Available>(second.library.list())
            assertEquals(listOf("scan.png"), stored.documents.map { it.displayName })
            second.close()
        } finally {
            NativeLocalDatabaseFiles.delete(databaseName)
        }
    }
}

private class IosByteDocumentSource(override val displayName: String, private val content: ByteArray) :
    DocumentImportSource {
    override val declaredSizeBytes: Long = content.size.toLong()

    override suspend fun read(maximumBytes: Long): DocumentReadResult = if (content.size.toLong() <= maximumBytes) {
        DocumentReadResult.Content(content)
    } else {
        DocumentReadResult.TooLarge
    }
}

private val PNG = hex(
    "89504e470d0a1a0a0000000d4948445200000002000000030806000000b9eade81" +
        "0000000b49444154789c636080020000090001fb52b8a90000000049454e44ae426082",
)

private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
    val offset = index * 2
    value.substring(offset, offset + 2).toInt(radix = 16).toByte()
}
