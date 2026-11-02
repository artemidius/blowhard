package io.github.artemidius.blowhard.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Device evidence for Android's activity-bound diagnostic export coordinator. */
@RunWith(AndroidJUnit4::class)
public class AndroidRunDiagnosticReportSaverDeviceTest {
    /** Proves a selected destination receives the complete sanitized report. */
    @Test
    public fun selectedDestinationReceivesCompleteReport(): Unit = runTest {
        val destination = Uri.parse("content://fixture/export")
        var written: Pair<Uri, String>? = null
        val saver = AndroidRunDiagnosticReportSaver.testSaver { uri, content ->
            written = uri to content
            true
        }
        val owner = Any()
        saver.bind(owner) { fileName ->
            assertEquals(RunDiagnosticReport.FILE_NAME, fileName)
            saver.complete(destination)
        }

        assertEquals(RunDiagnosticReportSaveResult.SAVED, saver.save(REPORT))
        assertEquals(destination to REPORT.content, written)
    }

    /** Proves dismissal and a missing activity never invoke the writer. */
    @Test
    public fun dismissalAndMissingHostNeverWrite(): Unit = runTest {
        var writes = 0
        val saver = AndroidRunDiagnosticReportSaver.testSaver { _, _ ->
            writes += 1
            true
        }

        assertEquals(RunDiagnosticReportSaveResult.UNAVAILABLE, saver.save(REPORT))
        val owner = Any()
        saver.bind(owner) { saver.complete(null) }
        assertEquals(RunDiagnosticReportSaveResult.CANCELLED, saver.save(REPORT))
        assertEquals(0, writes)
    }

    /** Proves a destination write failure remains content-safe. */
    @Test
    public fun failedContentWriteIsUnavailable(): Unit = runTest {
        val saver = AndroidRunDiagnosticReportSaver.testSaver { _, _ -> false }
        val owner = Any()
        saver.bind(owner) { saver.complete(Uri.parse("content://fixture/export")) }

        assertEquals(RunDiagnosticReportSaveResult.UNAVAILABLE, saver.save(REPORT))
    }
}

private val REPORT = RunDiagnosticReport(RunDiagnosticReport.FILE_NAME, "{\"safe\":true}")
