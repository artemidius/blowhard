# ADR 0068: iOS diagnostic export

## Status

Accepted.

## Context

iOS users need to export sanitized run diagnostics through a system destination surface. Shared code
must not retain native URLs, choose a destination silently, or leave sensitive temporary files behind.

## Decision

Write the bounded report atomically to the application temporary directory, then present a
`UIDocumentPickerViewController` configured for export-as-copy. The controller-bound saver owns at
most one suspended request. Completion, dismissal, cancellation, and controller disposal all delete
the temporary source immediately.

The exported filename is fixed by the shared diagnostic boundary and contains no conversation or run
identity. Only the sanitized JSON report reaches the native picker.

## Consequences

- Export always passes through an explicit iOS destination choice.
- Native URLs never enter portable UI state.
- Temporary report files have a short, deterministic lifetime.
- Simulator tests cover successful completion, dismissal, missing host, and cleanup.
