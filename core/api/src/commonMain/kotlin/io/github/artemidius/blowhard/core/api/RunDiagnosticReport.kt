package io.github.artemidius.blowhard.core.api

/** A bounded UTF-8 JSON document containing only allowlisted run metadata. */
public data class RunDiagnosticReport(public val suggestedFileName: String, public val content: String) {
    init {
        require(suggestedFileName == FILE_NAME) { "Diagnostic report file name is fixed" }
        require(content.isNotBlank()) { "Diagnostic report must not be blank" }
        require(content.length <= MAX_CONTENT_LENGTH) { "Diagnostic report is too large" }
    }

    /** Stable report constraints shared by generators and platform writers. */
    public companion object {
        /** Suggested file name deliberately excludes run or conversation identity. */
        public const val FILE_NAME: String = "blowhard-run-diagnostic.json"

        /** Maximum UTF-16 length accepted at the export boundary. */
        public const val MAX_CONTENT_LENGTH: Int = 65_536
    }
}

/** Generates a safe diagnostic document without choosing or writing a destination. */
public fun interface RunDiagnosticReportGenerator {
    /** Generates a report for [runId] through the passive inspection boundary. */
    public suspend fun generate(runId: RunId): RunDiagnosticReportResult
}

/** Result of generating one bounded diagnostic report. */
public sealed interface RunDiagnosticReportResult {
    /** The report is ready for an explicit platform save operation. */
    public data class Generated(public val report: RunDiagnosticReport) : RunDiagnosticReportResult

    /** The requested run is not available to the current process. */
    public data object NotFound : RunDiagnosticReportResult

    /** Safe metadata could not be encoded inside the report boundary. */
    public data object Unavailable : RunDiagnosticReportResult
}
