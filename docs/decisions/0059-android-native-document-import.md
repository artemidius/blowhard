# ADR 0059: Android native document import

## Status

Accepted.

## Context

The shared mobile document surface previously exposed a fail-closed placeholder. Android needs a
native selection flow and durable local library without leaking content URIs into portable state or
granting the UI unbounded filesystem access.

## Decision

Use Android's Storage Access Framework `OpenDocument` contract, limited to PDF, PNG, and JPEG. Keep
the activity-result launcher bound to the current activity while the application-scoped picker owns
at most one pending request. Activity destruction cancels that pending request safely.

Wrap the returned content URI in a `DocumentImportSource` that exposes only a display name, optional
size, and bounded stream reader. Copy accepted bytes immediately into the existing app-owned
SQLDelight document store after portable structural inspection. URI text never enters presentation
or durable metadata.

Own document and processing stores inside each replaceable Android application session. If optional
document storage cannot open, the live conversation graph still starts and document operations
remain explicitly unavailable. OCR remains a separate provider-wiring increment.

## Consequences

- Android users can select and persist supported local documents through the shared mobile UI.
- Imports retain the existing 20 MiB, PDF-page, image-pixel, and content-signature limits.
- Activity recreation rebinds the picker without recreating the application session.
- Local-data reset closes document stores before deleting their shared database.
