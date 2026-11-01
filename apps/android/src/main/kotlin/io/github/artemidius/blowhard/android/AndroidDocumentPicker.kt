package io.github.artemidius.blowhard.android

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.ui.presentation.DocumentPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Activity-bound Android document picker with application-scoped request state. */
internal class AndroidDocumentPicker(private val contentResolver: ContentResolver) : DocumentPicker {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: kotlinx.coroutines.CancellableContinuation<DocumentImportSource?>? = null

    fun bind(selectionLauncher: ActivityResultLauncher<Array<String>>) {
        launcher = selectionLauncher
    }

    fun unbind(selectionLauncher: ActivityResultLauncher<Array<String>>) {
        if (launcher !== selectionLauncher) return
        launcher = null
        pending?.resume(null)
        pending = null
    }

    fun complete(uri: Uri?) {
        val continuation = pending ?: return
        pending = null
        continuation.resume(uri?.let { AndroidDocumentImportSource.from(contentResolver, it) })
    }

    override suspend fun select(): DocumentImportSource? = withContext(Dispatchers.Main.immediate) {
        val activeLauncher = launcher ?: return@withContext null
        if (pending != null) return@withContext null
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            activeLauncher.launch(SUPPORTED_TYPES)
        }
    }
}

private val SUPPORTED_TYPES: Array<String> = arrayOf("application/pdf", "image/png", "image/jpeg")
