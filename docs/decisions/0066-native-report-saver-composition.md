# ADR 0066: Native report-saver composition

## Status

Accepted.

## Context

Run diagnostics are generated as bounded, sanitized JSON in shared application code. Each platform
must ask the user for an export destination without mutating a dependency graph already used by a
published runtime.

## Decision

Add `withReportSaver` to immutable application-runtime dependencies. The method replaces only the
platform export boundary and preserves conversation, document, meeting-note, and approval services
by identity.

## Consequences

- Android and iOS can compose native export surfaces after their host controllers exist.
- Export remains an explicit user action and never changes application-service ownership.
- Tests guard against losing previously configured capabilities during composition.
