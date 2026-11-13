# ADR 0060: iOS native document import

## Status

Accepted.

## Context

iOS needs the same bounded local-document workflow as Android without exposing security-scoped URLs
to portable presentation state or requiring SwiftUI to mirror Kotlin application services.

## Decision

Present `UIDocumentPickerViewController` from the exported Compose controller and request app-local
copies of PDF, PNG, and JPEG sources. The controller-bound picker owns at most one suspended request;
controller disposal cancels it and closes the application session.

Wrap the returned URL in a native `DocumentImportSource`. Check file metadata before reading, use
security-scoped access only for the bounded read, copy bytes into Kotlin-owned memory, and stop URL
access immediately. Portable inspection and import policy validate the copy before it enters the
native SQLDelight document store.

Document and processing stores are owned by the replaceable iOS application session and share the
protected app database. Failure to open this optional capability leaves conversation behavior live
while document operations remain unavailable. OCR provider wiring remains a separate increment.

## Consequences

- iOS users can import supported documents through a native, system-managed picker.
- Native URLs and security-scope lifetimes never enter shared UI state.
- Imports retain the same size, page, pixel, and content-signature limits on both mobile platforms.
- Local-data reset closes document stores before deleting protected database files.
