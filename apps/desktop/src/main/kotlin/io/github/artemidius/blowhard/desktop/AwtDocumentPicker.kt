package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** Desktop document picker that keeps the selected platform path inside its source adapter. */
internal object AwtDocumentPicker : DocumentPicker {
    override suspend fun select(): DocumentImportSource? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            runCatching {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Import a local document"
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isMultiSelectionEnabled = false
                    fileFilter = FileNameExtensionFilter("PDF and images", "pdf", "png", "jpg", "jpeg")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile?.toPath()?.let(::PathDocumentImportSource)
                } else {
                    null
                }
            }.onSuccess { selection ->
                if (continuation.isActive) continuation.resume(selection)
            }.onFailure { failure ->
                if (continuation.isActive) continuation.resumeWith(Result.failure(failure))
            }
        }
    }
}

internal class PathDocumentImportSource(private val path: Path) : DocumentImportSource {
    override val displayName: String = requireNotNull(path.fileName?.toString()?.takeIf(String::isNotBlank)) {
        "Selected document must have a filename"
    }

    override val declaredSizeBytes: Long? = path.regularFileSizeOrNull()

    override suspend fun read(maximumBytes: Long): DocumentReadResult = withContext(Dispatchers.IO) {
        if (maximumBytes !in 1..Int.MAX_VALUE.toLong()) return@withContext DocumentReadResult.Unavailable
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return@withContext DocumentReadResult.Unavailable
        try {
            Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream(initialCapacity(maximumBytes, declaredSizeBytes))
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) return@withContext DocumentReadResult.TooLarge
                    output.write(buffer, 0, read)
                }
                DocumentReadResult.Content(output.toByteArray())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DocumentReadResult.Unavailable
        }
    }
}

private fun Path.regularFileSizeOrNull(): Long? = try {
    takeIf { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }?.let(Files::size)
} catch (_: Exception) {
    null
}

private fun initialCapacity(maximumBytes: Long, declaredSizeBytes: Long?): Int =
    minOf(maximumBytes, declaredSizeBytes ?: DEFAULT_INITIAL_CAPACITY.toLong(), Int.MAX_VALUE.toLong()).toInt()

private const val BUFFER_SIZE = 8 * 1024
private const val DEFAULT_INITIAL_CAPACITY = BUFFER_SIZE
