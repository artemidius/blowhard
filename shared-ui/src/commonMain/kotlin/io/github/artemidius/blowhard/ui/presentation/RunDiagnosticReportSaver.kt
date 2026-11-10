package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.RunDiagnosticReport

/** Platform capability that explicitly asks the user where to save a diagnostic report. */
public fun interface RunDiagnosticReportSaver {
    /** Presents platform selection and writes [report] only after the user approves a destination. */
    public suspend fun save(report: RunDiagnosticReport): RunDiagnosticReportSaveResult
}

/** Content-safe outcome of a platform diagnostic save operation. */
public enum class RunDiagnosticReportSaveResult {
    /** The complete report was written to the selected destination. */
    SAVED,

    /** The user dismissed destination selection without writing a file. */
    CANCELLED,

    /** Selection or writing failed without exposing platform details. */
    UNAVAILABLE,
}
