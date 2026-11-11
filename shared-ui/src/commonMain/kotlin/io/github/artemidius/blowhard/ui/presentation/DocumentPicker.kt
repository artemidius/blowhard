package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentImportSource

/** Platform-owned document selection boundary; cancellation returns `null`. */
public fun interface DocumentPicker {
    /** Selects one source without exposing its platform path to shared presentation state. */
    public suspend fun select(): DocumentImportSource?
}
