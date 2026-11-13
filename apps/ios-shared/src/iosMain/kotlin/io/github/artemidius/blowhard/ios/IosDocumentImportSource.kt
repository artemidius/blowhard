@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

/** Bounded reader for the app-local copy returned by UIKit's document picker. */
internal class IosDocumentImportSource(private val url: NSURL) : DocumentImportSource {
    override val displayName: String = url.lastPathComponent?.takeIf(String::isNotBlank) ?: "selected-document"
    override val declaredSizeBytes: Long? = url.fileSizeOrNull()

    override suspend fun read(maximumBytes: Long): DocumentReadResult = withContext(Dispatchers.Default) {
        if (maximumBytes !in 1..Int.MAX_VALUE.toLong()) return@withContext DocumentReadResult.Unavailable
        if (declaredSizeBytes?.let { it > maximumBytes } == true) return@withContext DocumentReadResult.TooLarge
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return@withContext DocumentReadResult.Unavailable
            if (data.length > maximumBytes.toULong()) return@withContext DocumentReadResult.TooLarge
            DocumentReadResult.Content(data.copyBytes())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DocumentReadResult.Unavailable
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }
}

private fun NSURL.fileSizeOrNull(): Long? = try {
    val path = path ?: return null
    val value = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber
    value?.longLongValue?.takeIf { it >= 0 }
} catch (_: Exception) {
    null
}

private fun NSData.copyBytes(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return requireNotNull(bytes).reinterpret<ByteVar>().readBytes(size)
}
