package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AwtRunDiagnosticReportSaverTest {
    @Test
    fun `cancelled selection never invokes a write`() = runTest {
        var writes = 0
        val saver = AwtRunDiagnosticReportSaver(
            selectDestination = { null },
            writeReport = { _, _ -> writes += 1 },
        )

        assertEquals(RunDiagnosticReportSaveResult.CANCELLED, saver.save(report()))
        assertEquals(0, writes)
    }

    @Test
    fun `selected destination atomically replaces prior content with exact utf8 report`() = runTest {
        val directory = Files.createTempDirectory("blowhard-diagnostic-test-")
        try {
            val destination = directory.resolve("report.json")
            Files.writeString(destination, "incomplete previous report")
            val saver = AwtRunDiagnosticReportSaver(selectDestination = { destination })

            assertEquals(RunDiagnosticReportSaveResult.SAVED, saver.save(report()))
            assertEquals(REPORT_CONTENT, destination.readText())
            assertFalse(
                Files.list(directory).use { files ->
                    files.anyMatch { it.fileName.toString().endsWith(".tmp") }
                },
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write failure exposes no native detail`() = runTest {
        val saver = AwtRunDiagnosticReportSaver(
            selectDestination = { Path.of("unused-report.json") },
            writeReport = { _, _ -> error("private filesystem detail") },
        )

        assertEquals(RunDiagnosticReportSaveResult.UNAVAILABLE, saver.save(report()))
    }

    private fun report(): RunDiagnosticReport = RunDiagnosticReport(
        suggestedFileName = RunDiagnosticReport.FILE_NAME,
        content = REPORT_CONTENT,
    )
}

private const val REPORT_CONTENT = "{\"schemaVersion\":1}\n"
