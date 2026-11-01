# ADR 0056: Verify mobile restart recovery on native stores

## Status

Accepted.

## Context

Mobile conversation persistence is useful only if abrupt process loss remains safe. A queued or
running provider attempt must become interrupted after restart, retain its last durable snapshot,
and never be submitted again automatically. Storage failures must fail closed without exposing
native paths or database errors.

## Decision

Run host-level recovery tests against the real Android and iOS SQLDelight drivers. Each test writes
an active attempt, closes the store to model process loss, opens the same database through the
production history-session boundary, and proves that recovery happens exactly once without replay.

Add an Android corruption canary using invalid app-private database bytes. Android's SQLite
corruption handler must replace them with a fresh usable store, and presentation state must remain
empty without exposing the native path. The portable history-session tests continue to cover the
sanitized fail-closed fallback when a platform cannot recover its store.

Keep schema upgrade safety in the existing checked-in SQLDelight schema snapshots. The required
quality gate runs `verifySqlDelightMigration`, while native restart tests exercise the current
Android and iOS drivers.

## Consequences

- Android device and iOS simulator jobs prove restart behavior on their real SQLite drivers.
- Interrupted output remains inspectable, but no provider request is replayed.
- Repeated startup recovery is idempotent.
- Recoverable Android corruption produces an empty store without exposing a native path.
