# ADR 0053: Replaceable mobile application session

## Status

Accepted.

## Context

Erasing a mobile application database requires every active presenter, generation task, store, and
provider client to stop first. The UI must then receive a clean runtime that no longer references
deleted storage. Android and iOS need the same ordering and duplicate-request guarantees despite
using different native credential and database adapters.

## Decision

Use a portable replaceable-session owner around each platform composition root. It publishes the
current session as immutable state and performs reset in this order: close the current session,
erase host-owned data, create a fail-closed replacement, then publish the replacement atomically.

Once accepted, reset is non-cancellable so activity or scene changes cannot leave a half-closed
session. A concurrent reset fails immediately instead of queuing a second destructive operation.
Host shutdown uses the same operation lock and closes the current session once.

Native failures are converted into typed, content-free results. A close failure prevents database
erasure because the database may still be open. An erasure failure still publishes a clean
replacement so the UI can recover and report the incomplete deletion. A replacement failure closes
the owner and never presents the old, already-closed session as usable.

## Consequences

- Android and iOS share reset ordering, concurrency, cancellation, and lifetime behavior.
- Platform code supplies only session creation, session closure, and local-data erasure boundaries.
- The UI can recompose from the published replacement without retaining native resources.
- Session factories must return explicit unavailable services for expected startup failures.
