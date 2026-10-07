package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal class AwtRunDiagnosticReportSaver(
    private val selectDestination: (String) -> Path? = ::selectDiagnosticDestination,
    private val writeReport: (Path, String) -> Unit = ::writeUtf8Atomically,
) : RunDiagnosticReportSaver {
    override suspend fun save(report: RunDiagnosticReport): RunDiagnosticReportSaveResult = try {
        val destination = selectDestination(report.suggestedFileName)
            ?: return RunDiagnosticReportSaveResult.CANCELLED
        writeReport(destination, report.content)
        RunDiagnosticReportSaveResult.SAVED
    } catch (_: IOException) {
        RunDiagnosticReportSaveResult.UNAVAILABLE
    } catch (_: SecurityException) {
        RunDiagnosticReportSaveResult.UNAVAILABLE
    } catch (_: RuntimeException) {
        RunDiagnosticReportSaveResult.UNAVAILABLE
    }
}

private fun selectDiagnosticDestination(suggestedFileName: String): Path? {
    var selected: Path? = null
    val showDialog = {
        val owner = Frame.getFrames().firstOrNull { it.isVisible }
        val dialog = FileDialog(owner, "Export sanitized run diagnostic", FileDialog.SAVE)
        try {
            dialog.file = suggestedFileName
            dialog.isVisible = true
            val directory = dialog.directory
            val file = dialog.file
            if (directory != null && file != null) selected = Path.of(directory, file)
        } finally {
            dialog.dispose()
        }
    }
    if (EventQueue.isDispatchThread()) showDialog() else EventQueue.invokeAndWait(showDialog)
    return selected
}

private fun writeUtf8Atomically(destination: Path, content: String) {
    val absoluteDestination = destination.toAbsolutePath()
    val parent = checkNotNull(absoluteDestination.parent) { "Diagnostic destination requires a parent directory" }
    val temporary = Files.createTempFile(parent, ".blowhard-diagnostic-", ".tmp")
    try {
        Files.writeString(
            temporary,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        try {
            Files.move(
                temporary,
                absoluteDestination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, absoluteDestination, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}
