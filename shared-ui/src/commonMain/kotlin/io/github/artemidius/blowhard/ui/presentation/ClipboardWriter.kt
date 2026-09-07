package io.github.artemidius.blowhard.ui.presentation

/** Platform-neutral destination for explicitly copied conversation text. */
public fun interface ClipboardWriter {
    /** Attempts to replace local clipboard text with [text]. */
    public fun write(text: String): ClipboardWriteResult
}

/** Stable result of a local clipboard write. */
public enum class ClipboardWriteResult {
    /** The complete text was copied. */
    COPIED,

    /** The current platform session could not provide a clipboard. */
    UNAVAILABLE,
}
