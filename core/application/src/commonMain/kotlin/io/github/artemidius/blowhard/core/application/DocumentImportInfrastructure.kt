package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentId

/** Creates opaque identities for newly accepted documents. */
public fun interface DocumentIdFactory {
    /** Returns the next document identity. */
    public fun create(): DocumentId
}

/** Supplies import timestamps without tying common code to a platform clock. */
public fun interface DocumentImportClock {
    /** Returns a non-negative Unix epoch timestamp in milliseconds. */
    public fun nowEpochMilliseconds(): Long
}
