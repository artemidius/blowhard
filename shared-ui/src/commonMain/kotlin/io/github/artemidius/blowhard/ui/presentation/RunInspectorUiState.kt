package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection

/** Immutable presentation state for the shared run inspector. */
public sealed interface RunInspectorUiState {
    /** Run currently addressed by this state, when one has been selected. */
    public val runId: RunId?

    /** No run has been selected. */
    public data object Empty : RunInspectorUiState {
        override val runId: RunId? = null
    }

    /** A passive inspection lookup is in progress. */
    public data class Loading(override val runId: RunId) : RunInspectorUiState

    /** The selected run and its allowlisted operational metadata. */
    public data class Ready(
        public val inspection: RunInspection,
        public val exportStatus: RunDiagnosticExportStatus = RunDiagnosticExportStatus.IDLE,
    ) : RunInspectorUiState {
        override val runId: RunId = inspection.runId
    }

    /** The selected run is not owned by the current process. */
    public data class NotFound(override val runId: RunId) : RunInspectorUiState

    /** The lookup failed without exposing exception or provider details. */
    public data class Failed(override val runId: RunId) : RunInspectorUiState
}

/** Current user-visible state of one explicit diagnostic export attempt. */
public enum class RunDiagnosticExportStatus {
    /** No export has been requested for the current inspection. */
    IDLE,

    /** Report generation or platform saving is in progress. */
    SAVING,

    /** The report was saved successfully. */
    SAVED,

    /** Destination selection was dismissed without writing a file. */
    CANCELLED,

    /** The report could not be generated or saved safely. */
    FAILED,
}
