# ADR 0064: Mobile meeting-note sessions

## Status

Accepted.

## Context

Android and iOS can import documents, produce validated OCR extractions, and use those extractions in
conversation. Meeting-note generation also needs a durable local store and must follow the currently
verified Mistral model without weakening session reset guarantees.

## Decision

Open the platform meeting-note store beside document metadata and extraction stores in the same
application database. Each mobile document session owns all three stores and closes them before the
database can be erased.

Build `DefaultMeetingNotes` with the durable extraction store, the verified-model generation router,
platform-generated identities, and a platform clock. Publish the service through immutable runtime
composition. Approval remains fail-closed until the separate exact-consent capability is composed.

## Consequences

- Android and iOS can generate, edit, look up, and export structured notes from processed documents.
- Model changes affect subsequent generation without exposing credentials to UI state.
- Notes survive application-session restarts and participate in local-data erasure.
- Native tests prove import, OCR, note generation, and restart persistence without external requests.
