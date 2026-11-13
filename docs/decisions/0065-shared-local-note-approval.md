# ADR 0065: Shared exact-consent meeting-note approval

## Status

Accepted.

## Context

Publishing an approved meeting-note copy is a durable mutation. Desktop already required an exact
proposal, explicit user consent, idempotent write receipt, and recovery after interruption. Android
and iOS need the same semantics without platform-specific policy forks.

## Decision

Move the local meeting-note approval boundary into portable application composition. Platform hosts
supply only a clock and opaque identity generator. The shared port prepares an exact copy and
destination, requires approve or deny, executes only the bound proposal, and reconciles the durable
operation receipt if cancellation interrupts the result path.

Compose this port on desktop, Android, and iOS only when a meeting-note store is available. Otherwise
retain the fail-closed approval implementation.

## Consequences

- All supported platforms share one consent and recovery policy.
- Approval never writes before an exact proposal is presented.
- Reconciliation distinguishes committed, interrupted, and uncertain outcomes without replay.
- Native persistence tests prove approved copies survive mobile session restart.
