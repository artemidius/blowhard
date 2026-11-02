# ADR 0067: Android diagnostic export

## Status

Accepted.

## Context

Android users need to export sanitized run diagnostics without granting broad storage access or
allowing application code to choose an undisclosed destination.

## Decision

Use the Storage Access Framework `CreateDocument` contract with JSON content type. The activity owns
the launcher, while the application-scoped saver owns at most one suspended request and survives
configuration recreation. After an explicit destination is returned, write the complete bounded
report through `ContentResolver` on the I/O dispatcher.

Unbinding the owning activity completes any pending request without writing. Missing hosts,
concurrent requests, and write failures return content-safe unavailable outcomes.

## Consequences

- Export requires an explicit system destination choice.
- No storage permission or filesystem path enters shared application state.
- Activity recreation cannot leak a continuation or silently write a report.
- Unit tests cover success, dismissal, missing host, and write failure without persisting user data.
