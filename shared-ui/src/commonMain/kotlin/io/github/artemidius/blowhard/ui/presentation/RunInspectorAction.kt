package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.RunId

/** User intent handled by the content-free run inspector presentation. */
public sealed interface RunInspectorAction {
    /** Opens the inspection identified by [runId]. */
    public data class Open(public val runId: RunId) : RunInspectorAction

    /** Reloads the currently selected inspection without starting provider work. */
    public data object Refresh : RunInspectorAction

    /** Generates and saves an allowlisted JSON report after explicit destination selection. */
    public data object Export : RunInspectorAction
}
