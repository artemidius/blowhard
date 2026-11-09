package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal class AwtClipboardWriter(
    private val sessionAvailable: () -> Boolean,
    private val nativeWrite: (String) -> Unit = { text ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    },
) : ClipboardWriter {
    override fun write(text: String): ClipboardWriteResult {
        if (!sessionAvailable()) return ClipboardWriteResult.UNAVAILABLE
        return try {
            nativeWrite(text)
            ClipboardWriteResult.COPIED
        } catch (_: RuntimeException) {
            ClipboardWriteResult.UNAVAILABLE
        }
    }
}
