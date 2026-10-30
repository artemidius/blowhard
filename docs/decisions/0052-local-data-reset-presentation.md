# ADR 0052: Local-data reset presentation boundary

## Status

Accepted.

## Context

Mobile settings need an explicit destructive action for removing the saved API credential and the
application database. The platform host must close and replace the active application runtime while
performing that operation. A presenter owned by the runtime being closed cannot reliably report
progress or the final outcome.

## Decision

Keep confirmation and reset progress in a small presenter owned directly by the mobile host. The
presenter invokes one suspendable erasure boundary and exposes only sanitized erased or failed
outcomes. It never receives credential values, database paths, or native error details.

The shared settings UI requires an explicit confirmation before invoking the boundary, disables the
action while erasure is active, and ignores duplicate confirmations. Platform hosts remain
responsible for stopping active work, closing stores and provider clients, deleting each native
data target, and publishing a clean replacement runtime.

## Consequences

- Confirmation state survives replacement of the application runtime without retaining a secret.
- Android and iOS share one deterministic interaction state machine and duplicate-request policy.
- Native erasure order and error handling stay outside Compose and remain independently testable.
- Desktop is unchanged because the mobile reset presenter is an optional root dependency.
