# ADR 0024: Explicit sanitized diagnostic export

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The run inspector exposes useful operational evidence without conversation content, but copying
that data into a support artifact requires a platform write boundary. Exporting arbitrary runtime
objects or applying regex redaction at write time would make prompts, generated output,
credentials, provider bodies, and filesystem details easy to disclose accidentally.

Desktop save dialogs and filesystem behavior also do not belong in the shared UI or application
core. Mobile clients will need different destination selection while consuming the same report
contract.

## Decision

Generate a versioned JSON document exclusively from `RunInspection`. The application generator
performs a second passive inspection lookup and has no access to content-bearing snapshots. Keep a
fixed, non-identifying suggested filename and reject documents larger than 65,536 UTF-16 code
units. Preserve unknown parameters and usage counts as JSON `null` rather than invented values.

Represent generation, user cancellation, successful saving, and unavailable saving as typed
outcomes. Inject destination selection into the shared presenter through
`RunDiagnosticReportSaver`; the shared layer never receives a filesystem path or native error.

On desktop, show the native AWT save dialog only after the user selects **Export JSON**. Write the
exact UTF-8 document to a temporary file in the selected directory, then replace the destination
with an atomic move when supported. Delete any temporary file after success or failure. Surface
only generic content-safe status to the UI.

## Alternatives considered

### Export the conversation snapshot and redact text fields

Rejected because a denylist can miss new sensitive fields. Unsafe data is omitted by construction
before the platform adapter receives anything.

### Let the shared UI write a supplied path

Rejected because paths and native selection are platform concerns, and a path-taking API makes
unprompted writes easier to introduce.

### Include run identity in the suggested filename

Rejected because filenames are commonly synchronized, logged, or shown in recent-file lists. A
fixed filename avoids leaking even opaque identifiers outside the report.

### Stream directly to the selected destination

Rejected because an interrupted write could leave a plausible but incomplete diagnostic. The
desktop adapter stages the complete bounded document before replacement.

## Consequences

- Desktop users explicitly choose every export destination.
- Shared mobile clients can provide their own saver without changing report generation or UI
  intent.
- Prompt text, generated output, credentials, provider references, native errors, and paths are
  absent from the default report boundary.
- The report is deterministic and versioned, making regression tests and downstream tooling
  practical.
- A completed export may replace a destination the user explicitly selected in the native save
  dialog.
