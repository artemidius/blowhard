# ADR 0051: Mobile local database erasure

## Status

Accepted.

## Context

Mobile reset must remove the complete application-owned SQLite database, not only rows visible in
the current screen. SQLite may keep journal, WAL, and shared-memory sidecars beside the primary
file. Deleting an open database is unsafe and can leave active stores with invalid handles.

## Decision

Provide explicit Android and iOS database-file erasure boundaries in the persistence adapter.
Callers must first stop generation, close presentation resources, and close every store using the
database. The boundary then removes the primary file and known SQLite sidecars, verifies that none
remain, and reports a sanitized deleted, absent, or failed result.

The operation is idempotent. Android device and iOS simulator integration tests create durable
content, close it, erase the database, reopen an empty database, and erase it again.

## Consequences

- One operation can remove conversations and future document, processing, note, and approval rows
  that share the application database.
- The secure API credential remains a separate OS-vault record and requires an explicit deletion.
- Export files are not stored in this database; temporary export ownership and cleanup are handled
  by the native export stage.
- A user-facing reset coordinator must enforce the required shutdown order before invoking erasure.
