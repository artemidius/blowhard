package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSObject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePNG
import kotlin.coroutines.resume

/** UIKit document picker that keeps native URLs outside shared presentation state. */
internal class IosDocumentPicker :
    NSObject(),
    UIDocumentPickerDelegateProtocol,
    DocumentPicker {
    private var presenter: UIViewController? = null
    private var pending: CancellableContinuation<DocumentImportSource?>? = null

    fun bind(viewController: UIViewController) {
        presenter = viewController
    }

    fun unbind(viewController: UIViewController) {
        if (presenter !== viewController) return
        presenter = null
        complete(null)
    }

    override suspend fun select(): DocumentImportSource? = withContext(Dispatchers.Main) {
        val activePresenter = presenter ?: return@withContext null
        if (pending != null) return@withContext null
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypePDF, UTTypePNG, UTTypeJPEG),
                asCopy = true,
            )
            picker.delegate = this@IosDocumentPicker
            activePresenter.presentViewController(picker, animated = true, completion = null)
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        complete(null)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
        complete(IosDocumentImportSource(didPickDocumentAtURL))
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        complete((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::IosDocumentImportSource))
    }

    private fun complete(source: DocumentImportSource?) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(source)
    }
}
