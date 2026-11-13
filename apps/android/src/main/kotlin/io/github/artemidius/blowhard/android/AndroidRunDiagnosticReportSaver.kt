package io.github.artemidius.blowhard.android

import android.content.ContentResolver
import android.net.Uri
import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

/** Android Storage Access Framework exporter for sanitized diagnostic JSON. */
internal class AndroidRunDiagnosticReportSaver private constructor(
    private val writeReport: suspend (Uri, String) -> Boolean,
) : RunDiagnosticReportSaver {
    private var binding: ExportBinding? = null
    private var pending: CancellableContinuation<Uri?>? = null

    constructor(contentResolver: ContentResolver) : this(
        writeReport = { destination, content -> contentResolver.writeUtf8(destination, content) },
    )

    fun bind(owner: Any, launch: (String) -> Unit) {
        binding = ExportBinding(owner, launch)
    }

    fun unbind(owner: Any) {
        if (binding?.owner !== owner) return
        binding = null
        complete(null)
    }

    fun complete(destination: Uri?) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(destination)
    }

    override suspend fun save(report: RunDiagnosticReport): RunDiagnosticReportSaveResult =
        when (val selected = selectDestination(report.suggestedFileName)) {
            is DestinationSelection.Selected -> writeSafely(selected.uri, report.content)
            DestinationSelection.Cancelled -> RunDiagnosticReportSaveResult.CANCELLED
            DestinationSelection.Unavailable -> RunDiagnosticReportSaveResult.UNAVAILABLE
        }

    private suspend fun selectDestination(fileName: String): DestinationSelection =
        withContext(Dispatchers.Main.immediate) {
            val activeBinding = binding ?: return@withContext DestinationSelection.Unavailable
            if (pending != null) return@withContext DestinationSelection.Unavailable
            val destination = suspendCancellableCoroutine { continuation ->
                pending = continuation
                continuation.invokeOnCancellation {
                    if (pending === continuation) pending = null
                }
                activeBinding.launch(fileName)
            }
            destination?.let(DestinationSelection::Selected) ?: DestinationSelection.Cancelled
        }

    private suspend fun writeSafely(destination: Uri, content: String): RunDiagnosticReportSaveResult =
        withContext(Dispatchers.IO) {
            try {
                if (writeReport(destination, content)) {
                    RunDiagnosticReportSaveResult.SAVED
                } else {
                    RunDiagnosticReportSaveResult.UNAVAILABLE
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RunDiagnosticReportSaveResult.UNAVAILABLE
            }
        }

    companion object {
        internal fun testSaver(writeReport: suspend (Uri, String) -> Boolean): AndroidRunDiagnosticReportSaver =
            AndroidRunDiagnosticReportSaver(writeReport)
    }
}

private fun ContentResolver.writeUtf8(destination: Uri, content: String): Boolean {
    val stream = openOutputStream(destination, "wt") ?: return false
    stream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(content) }
    return true
}

private data class ExportBinding(val owner: Any, val launch: (String) -> Unit)

private sealed interface DestinationSelection {
    data class Selected(val uri: Uri) : DestinationSelection
    data object Cancelled : DestinationSelection
    data object Unavailable : DestinationSelection
}
