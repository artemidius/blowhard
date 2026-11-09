package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals

class AwtClipboardWriterTest {
    @Test
    fun `available session writes exact text`() {
        var copied = ""
        val writer = AwtClipboardWriter(sessionAvailable = { true }, nativeWrite = { copied = it })

        val result = writer.write("Spaces and Unicode: ä—日")

        assertEquals(ClipboardWriteResult.COPIED, result)
        assertEquals("Spaces and Unicode: ä—日", copied)
    }

    @Test
    fun `headless session never touches native clipboard`() {
        var writes = 0
        val writer = AwtClipboardWriter(sessionAvailable = { false }, nativeWrite = { writes += 1 })

        assertEquals(ClipboardWriteResult.UNAVAILABLE, writer.write("text"))
        assertEquals(0, writes)
    }

    @Test
    fun `native clipboard failure is redacted`() {
        val writer = AwtClipboardWriter(
            sessionAvailable = { true },
            nativeWrite = { error("private native clipboard detail") },
        )

        assertEquals(ClipboardWriteResult.UNAVAILABLE, writer.write("text"))
    }
}
