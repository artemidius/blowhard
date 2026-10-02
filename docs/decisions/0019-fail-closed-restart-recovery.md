# ADR 0019: Fail-closed restart recovery

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

A process can stop after an attempt is persisted as queued or running. On the next launch, local
storage cannot prove whether the provider accepted work, produced additional output, or incurred
cost after the last checkpoint. Leaving the attempt active would misrepresent reality. Replaying it
could duplicate work or cost and would create a new outcome under an old run identity.

Recovery must preserve partial text and the exact source, model, and generation controls that were
selected for the original attempt. It must also tolerate system-clock adjustments and repeated
startup calls.

## Decision

Expose one explicit recovery operation on `ConversationStore`. It atomically changes every queued
or running attempt to interrupted and returns the number of affected attempts. All messages,
attempt identities, routing/configuration snapshots, and terminal attempts remain unchanged.

Set the recovered update timestamp to the later of the stored timestamp and the supplied recovery
time. Repeating recovery is a no-op because interrupted is terminal for this policy.

Keep recovery independent from `TextGenerationPort`. The operation records local uncertainty only;
it never starts, resumes, retries, or replays provider work. A later startup coordinator will call
recovery before loading history into presentation state.

## Alternatives considered

### Replay queued and running attempts automatically

Rejected because provider acceptance is ambiguous after process loss. Replay can duplicate cost or
output and cannot safely reuse the original attempt identity.

### Convert recovered attempts to failed or cancelled

Rejected because neither outcome is known. Interrupted communicates that the local process lost
authoritative execution state without inventing a provider failure or user cancellation.

### Repair state in presentation code

Rejected because every client must observe the same durable lifecycle policy. Recovery belongs to
the core storage contract and must complete before a UI can expose history.

### Replace each transcript separately

Rejected because recovery is a database-wide state transition. One constrained update is atomic,
idempotent, and avoids partially recovered startup state.

## Consequences

- Restart cannot silently resume or repeat model work.
- Partial output and immutable request provenance remain available for inspection.
- Recovery stays efficient regardless of the number of stored conversations.
- Affected-row count provides bounded startup evidence without exposing conversation content.
- The desktop composition root must still open storage, invoke recovery, and map history in a later
  increment.
