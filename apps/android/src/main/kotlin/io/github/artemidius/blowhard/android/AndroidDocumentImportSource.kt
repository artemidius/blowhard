package io.github.artemidius.blowhard.android

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Bounded, one-shot reader for a document selected through Android's storage access framework. */
internal class AndroidDocumentImportSource private constructor(
    override val displayName: String,
    override val declaredSizeBytes: Long?,
    private val openStream: () -> InputStream?,
) : DocumentImportSource {
    override suspend fun read(maximumBytes: Long): DocumentReadResult = withContext(Dispatchers.IO) {
        if (maximumBytes !in 1..Int.MAX_VALUE.toLong()) return@withContext DocumentReadResult.Unavailable
        try {
            val stream = openStream() ?: return@withContext DocumentReadResult.Unavailable
            stream.use { input -> input.readBounded(maximumBytes, declaredSizeBytes) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DocumentReadResult.Unavailable
        }
    }

    companion object {
        fun from(contentResolver: ContentResolver, uri: Uri): AndroidDocumentImportSource {
            val metadata = contentResolver.queryMetadata(uri)
            return AndroidDocumentImportSource(
                displayName = metadata.name,
                declaredSizeBytes = metadata.size,
                openStream = { contentResolver.openInputStream(uri) },
            )
        }

        internal fun testSource(
            name: String,
            size: Long?,
            openStream: () -> InputStream?,
        ): AndroidDocumentImportSource = AndroidDocumentImportSource(name, size, openStream)
    }
}

private suspend fun InputStream.readBounded(maximumBytes: Long, declaredSize: Long?): DocumentReadResult {
    val capacity = minOf(maximumBytes, declaredSize ?: BUFFER_SIZE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    val output = ByteArrayOutputStream(capacity)
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maximumBytes) return DocumentReadResult.TooLarge
        output.write(buffer, 0, count)
    }
    return DocumentReadResult.Content(output.toByteArray())
}

private fun ContentResolver.queryMetadata(uri: Uri): DocumentMetadata = try {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        DocumentMetadata(cursor.safeName(uri), cursor.safeSize())
    } ?: DocumentMetadata(uri.safeFallbackName(), null)
} catch (_: Exception) {
    DocumentMetadata(uri.safeFallbackName(), null)
}

private fun Cursor.safeName(uri: Uri): String {
    val index = getColumnIndex(OpenableColumns.DISPLAY_NAME)
    val queried = if (index >= 0 && !isNull(index)) {
        getString(index).takeIf(String::isNotBlank)
    } else {
        null
    }
    return queried ?: uri.safeFallbackName()
}

private fun Cursor.safeSize(): Long? {
    val index = getColumnIndex(OpenableColumns.SIZE)
    return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it >= 0 } else null
}

private fun Uri.safeFallbackName(): String = lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    ?: "selected-document"

private data class DocumentMetadata(val name: String, val size: Long?)

private const val BUFFER_SIZE = 8 * 1024
