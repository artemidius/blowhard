# ADR 0021: Bounded durable run checkpoints

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Desktop startup can recover and display durable transcripts, but newly created activity remained
process-local. Writing every streamed text delta would amplify SQLite work, while writing only a
terminal result would lose all useful partial output after a process interruption. Provider work
must also not begin if the initial attempt cannot be made durable.

Metadata and transcript replacement were previously separate store operations. That boundary could
leave updated metadata beside an old transcript if a later write failed.

## Decision

Add one atomic `ConversationStore.saveCheckpoint` operation for conversation metadata and its
complete transcript aggregate. Preserve an existing creation timestamp and roll back both metadata
and transcript when any constraint or write fails.

Let `DefaultConversationRuns` own checkpoint scheduling when a store is supplied by the composition
root. Persist the queued attempt before creating provider work. Persist the first running state,
then checkpoint streaming output after either 512 additional Kotlin characters or two seconds of
run-clock time. Always checkpoint succeeded, failed, and cancelled outcomes before publishing the
terminal snapshot.

Each run receives stable durable user and assistant message identities derived from its opaque run
identity. A new attempt loads and extends the existing domain transcript, preserving earlier
attempts and immutable routing and generation-parameter snapshots. Presentation remains unaware of
SQLite and continues to observe only the shared run contract.

If the initial checkpoint fails, reject the start with `STORAGE_UNAVAILABLE` and do not invoke the
provider. If a later checkpoint fails, stop collection and publish a content-safe storage failure
for the process-local run. The last successful nonterminal database state remains recoverable as
interrupted on restart; no automatic replay occurs. When storage was unavailable at application
startup, the explicitly labeled process-local degraded mode remains available without a hidden
fallback database.

## Alternatives considered

### Persist every text delta

Rejected because provider chunk sizes are not a useful transaction boundary and may approach one
write per token. Atomicity would not compensate for avoidable write amplification.

### Persist only terminal outcomes

Rejected because a crash during a long response would discard all partial output even when useful
checkpoints could be written at a bounded cadence.

### Let the presenter save rendered state

Rejected because presentation models omit durable configuration and failure details, and mobile or
headless clients could bypass the persistence rule. Checkpoint policy belongs to the application
lifecycle.

### Continue generation after a checkpoint failure

Rejected because the UI could imply durable history while the stored aggregate was stale. Stopping
with a safe typed failure makes the loss of durability explicit.

## Consequences

- Completed, failed, cancelled, and bounded partial attempts survive a desktop restart.
- Provider execution never begins before the queued attempt is durable when storage is connected.
- One atomic database transaction keeps conversation metadata and transcript state consistent.
- The same application policy is reusable by future Android, iOS, and headless composition roots.
- A transcript checkpoint currently replaces all owned message and attempt rows; more granular
  storage optimization remains a separate increment.
- User-confirmed local deletion is defined separately by
  [`ADR 0022`](0022-confirmed-local-conversation-deletion.md).
