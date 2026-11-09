# ADR 0006: Own run execution in the application layer

- Status: accepted
- Date: 2026-11-09

## Context

Starting, observing, and cancelling generation must behave identically in every client. A provider
adapter must not decide lifecycle policy, and observing state must never create a second request.
The offline demonstration also needs realistic success and failure behavior without credentials or
network access.

## Decision

- `DefaultConversationRuns` owns a supervisor scope and exactly one collection of the generation
  port for each accepted attempt.
- A conversation has at most one active attempt. A semaphore independently bounds total provider
  concurrency; excess attempts remain visibly queued.
- Each attempt has an immutable ID and a hot state stream retained by an in-memory repository.
  Multiple observers share this stream and cannot cancel the underlying work by leaving it.
- Run IDs, transition timestamps, and coroutine context are injected. Tests therefore control
  identity and virtual time without sleeping or reading a wall clock.
- Cancellation is explicit, joins owned work, preserves partial output, and writes one terminal
  snapshot. Terminal snapshots reject all later events.
- Provider failures cross the boundary only as typed, client-safe categories. Unexpected exceptions
  are converted to a redacted failure without their message or payload.
- The fixture adapter implements the same outbound port as a future live provider. It offers named
  success, classified failure, rate-limit, and interrupted-stream scenarios with virtual-time-aware
  chunk delays.
- No automatic retry occurs in this increment. Replay requires transport-specific evidence that a
  request is safe to repeat, so retry policy is deferred to the streaming transport work.

## Consequences

The lifecycle can be exercised headlessly on every Kotlin target before UI or network code exists.
Same-conversation exclusion and global concurrency are separate rules, cancellation remains
structured, and a provider adapter cannot create unowned work through the port contract.

Run state is process-local and intentionally not durable. Restart recovery, database retention, and
attempt migration remain persistence concerns. Fixture output is synthetic and must be presented as
Demo behavior rather than evidence of a live Mistral response.
