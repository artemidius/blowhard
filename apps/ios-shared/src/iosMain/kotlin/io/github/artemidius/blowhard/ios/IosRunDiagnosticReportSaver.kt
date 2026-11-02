@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.core.api.RunDiagnosticReport
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaveResult
import io.github.artemidius.blowhard.ui.presentation.RunDiagnosticReportSaver
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.temporaryDirectory
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** UIKit exporter for bounded, sanitized diagnostic JSON files. */
internal class IosRunDiagnosticReportSaver private constructor(
    private val writeTemporary: (RunDiagnosticReport) -> NSURL?,
    private val presentExport: (UIViewController, NSURL, UIDocumentPickerDelegateProtocol) -> Boolean,
    private val deleteTemporary: (NSURL) -> Unit,
) : RunDiagnosticReportSaver {
    private var presenter: UIViewController? = null
    private var pending: CancellableContinuation<RunDiagnosticReportSaveResult>? = null
    private var temporaryUrl: NSURL? = null
    private var exportDelegate: IosDocumentExportDelegate? = null

    constructor() : this(::writeTemporaryReport, ::presentExportPicker, ::deleteTemporaryReport)

    fun bind(viewController: UIViewController) {
        presenter = viewController
    }

    fun unbind(viewController: UIViewController) {
        if (presenter !== viewController) return
        presenter = null
        complete(RunDiagnosticReportSaveResult.CANCELLED)
    }

    override suspend fun save(report: RunDiagnosticReport): RunDiagnosticReportSaveResult =
        withContext(Dispatchers.Main.immediate) {
            val activePresenter = presenter ?: return@withContext RunDiagnosticReportSaveResult.UNAVAILABLE
            if (pending != null) return@withContext RunDiagnosticReportSaveResult.UNAVAILABLE
            val temporary = writeTemporary(report) ?: return@withContext RunDiagnosticReportSaveResult.UNAVAILABLE
            temporaryUrl = temporary
            suspendCancellableCoroutine { continuation ->
                pending = continuation
                val delegate = IosDocumentExportDelegate(::complete)
                exportDelegate = delegate
                continuation.invokeOnCancellation {
                    if (pending === continuation) {
                        pending = null
                        cleanupTemporary()
                    }
                }
                if (!presentExport(activePresenter, temporary, delegate)) {
                    complete(RunDiagnosticReportSaveResult.UNAVAILABLE)
                }
            }
        }

    internal fun completeForTest(result: RunDiagnosticReportSaveResult) {
        complete(result)
    }

    private fun complete(result: RunDiagnosticReportSaveResult) {
        val continuation = pending ?: return
        pending = null
        cleanupTemporary()
        if (continuation.isActive) continuation.resume(result)
    }

    private fun cleanupTemporary() {
        temporaryUrl?.let(deleteTemporary)
        temporaryUrl = null
        exportDelegate = null
    }

    companion object {
        internal fun testSaver(
            writeTemporary: (RunDiagnosticReport) -> NSURL?,
            presentExport: (UIViewController, NSURL, UIDocumentPickerDelegateProtocol) -> Boolean,
            deleteTemporary: (NSURL) -> Unit,
        ): IosRunDiagnosticReportSaver = IosRunDiagnosticReportSaver(
            writeTemporary,
            presentExport,
            deleteTemporary,
        )
    }
}

private class IosDocumentExportDelegate(private val complete: (RunDiagnosticReportSaveResult) -> Unit) :
    NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        complete(RunDiagnosticReportSaveResult.CANCELLED)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
        complete(RunDiagnosticReportSaveResult.SAVED)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val result = if (didPickDocumentsAtURLs.isEmpty()) {
            RunDiagnosticReportSaveResult.CANCELLED
        } else {
            RunDiagnosticReportSaveResult.SAVED
        }
        complete(result)
    }
}

private fun writeTemporaryReport(report: RunDiagnosticReport): NSURL? = try {
    val destination = NSFileManager.defaultManager.temporaryDirectory
        .URLByAppendingPathComponent(report.suggestedFileName)
        ?: return null
    val written = NSString.create(string = report.content).writeToURL(
        destination,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    destination.takeIf { written }
} catch (_: Exception) {
    null
}

private fun presentExportPicker(
    presenter: UIViewController,
    url: NSURL,
    delegate: UIDocumentPickerDelegateProtocol,
): Boolean = try {
    val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
    picker.delegate = delegate
    presenter.presentViewController(picker, animated = true, completion = null)
    true
} catch (_: Exception) {
    false
}

private fun deleteTemporaryReport(url: NSURL) {
    try {
        NSFileManager.defaultManager.removeItemAtURL(url, null)
    } catch (_: Exception) {
        // Best-effort cleanup only; the OS also owns the temporary directory lifecycle.
    }
}
