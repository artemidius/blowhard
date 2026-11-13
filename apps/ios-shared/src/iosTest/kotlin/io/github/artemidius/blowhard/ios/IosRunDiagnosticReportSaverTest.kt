@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals

/** Simulator evidence for the UIKit diagnostic export coordinator. */
internal class IosRunDiagnosticReportSaverTest {
    @Test
    internal fun selectedExportCleansTemporaryFileAfterCompletion() = runTest {
        val temporary = NSURL(fileURLWithPath = "/tmp/${RunDiagnosticReport.FILE_NAME}")
        val deleted = mutableListOf<NSURL>()
        lateinit var saver: IosRunDiagnosticReportSaver
        saver = IosRunDiagnosticReportSaver.testSaver(
            writeTemporary = { temporary },
            presentExport = { _, url, _ ->
                assertEquals(temporary, url)
                saver.completeForTest(RunDiagnosticReportSaveResult.SAVED)
                true
            },
            deleteTemporary = { deleted.add(it) },
        )
        saver.bind(UIViewController())

        assertEquals(RunDiagnosticReportSaveResult.SAVED, saver.save(REPORT))
        assertEquals(listOf(temporary), deleted)
    }

    @Test
    internal fun missingPresenterAndDismissalNeverLeaveTemporaryFiles() = runTest {
        val temporary = NSURL(fileURLWithPath = "/tmp/${RunDiagnosticReport.FILE_NAME}")
        val deleted = mutableListOf<NSURL>()
        lateinit var saver: IosRunDiagnosticReportSaver
        saver = IosRunDiagnosticReportSaver.testSaver(
            writeTemporary = { temporary },
            presentExport = { _, _, _ ->
                saver.completeForTest(RunDiagnosticReportSaveResult.CANCELLED)
                true
            },
            deleteTemporary = { deleted.add(it) },
        )

        assertEquals(RunDiagnosticReportSaveResult.UNAVAILABLE, saver.save(REPORT))
        saver.bind(UIViewController())
        assertEquals(RunDiagnosticReportSaveResult.CANCELLED, saver.save(REPORT))
        assertEquals(listOf(temporary), deleted)
    }

    @Test
    internal fun controllerDisposalCancelsPendingExportAndCleansTemporaryFile() = runTest {
        val controller = UIViewController()
        val temporary = NSURL(fileURLWithPath = "/tmp/${RunDiagnosticReport.FILE_NAME}")
        val deleted = mutableListOf<NSURL>()
        lateinit var saver: IosRunDiagnosticReportSaver
        saver = IosRunDiagnosticReportSaver.testSaver(
            writeTemporary = { temporary },
            presentExport = { _, _, _ ->
                saver.unbind(controller)
                true
            },
            deleteTemporary = { deleted.add(it) },
        )
        saver.bind(controller)

        assertEquals(RunDiagnosticReportSaveResult.CANCELLED, saver.save(REPORT))
        assertEquals(listOf(temporary), deleted)
    }

    @Test
    internal fun presentationFailureReturnsUnavailableAndCleansTemporaryFile() = runTest {
        val temporary = NSURL(fileURLWithPath = "/tmp/${RunDiagnosticReport.FILE_NAME}")
        val deleted = mutableListOf<NSURL>()
        val saver = IosRunDiagnosticReportSaver.testSaver(
            writeTemporary = { temporary },
            presentExport = { _, _, _ -> false },
            deleteTemporary = { deleted.add(it) },
        )
        saver.bind(UIViewController())

        assertEquals(RunDiagnosticReportSaveResult.UNAVAILABLE, saver.save(REPORT))
        assertEquals(listOf(temporary), deleted)
    }
}

private val REPORT = RunDiagnosticReport(RunDiagnosticReport.FILE_NAME, "{\"safe\":true}")
