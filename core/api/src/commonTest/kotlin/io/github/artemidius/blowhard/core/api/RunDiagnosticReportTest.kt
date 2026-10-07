package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RunDiagnosticReportTest {
    @Test
    fun `report enforces fixed filename and bounded nonblank content`() {
        assertFailsWith<IllegalArgumentException> {
            RunDiagnosticReport("run-secret.json", "{}")
        }
        assertFailsWith<IllegalArgumentException> {
            RunDiagnosticReport(RunDiagnosticReport.FILE_NAME, "  ")
        }
        assertFailsWith<IllegalArgumentException> {
            RunDiagnosticReport(
                RunDiagnosticReport.FILE_NAME,
                "x".repeat(RunDiagnosticReport.MAX_CONTENT_LENGTH + 1),
            )
        }
    }
}
