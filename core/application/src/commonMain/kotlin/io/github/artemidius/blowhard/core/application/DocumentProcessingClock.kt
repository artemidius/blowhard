package io.github.artemidius.blowhard.core.application

/** Supplies OCR completion timestamps without coupling common code to a platform clock. */
public fun interface DocumentProcessingClock {
    /** Returns a non-negative Unix epoch timestamp in milliseconds. */
    public fun nowEpochMilliseconds(): Long
}
