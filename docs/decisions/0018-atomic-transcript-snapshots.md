# ADR 0018: Atomic transcript and attempt snapshots

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Durable history must retain more than rendered text. A regenerated response is a distinct attempt,
and each attempt must preserve its own source, model, generation controls, state, timing, and safe
failure evidence. Persisting shared presentation models would mix transient UI state with recovery
data. Writing each streaming token independently would also create unnecessary transactions and
make partially updated aggregates easier to observe.

The version-1 database contains conversation metadata only. Its committed fixture must continue to
open without losing existing records.

## Decision

Represent one stored transcript as a validated domain aggregate containing ordered messages and
generation attempts. Introduce a typed `MessageId`; keep message identity separate from `RunId`.
Each attempt references exactly one stored user message and one stored assistant message and
snapshots its `GenerationTarget`, `GenerationParameters`, timestamps, status, and optional
`RunFailure`.

Persist messages and attempts in normalized SQLite tables. Replace one transcript in a single
transaction after confirming its conversation exists. Delete attempts, messages, and conversation
metadata explicitly in dependency order within one transaction. Keep foreign keys in the schema as
a defensive layer, but do not rely on driver-specific foreign-key defaults for deletion
correctness. A failed replacement leaves the previous transcript intact.

Add schema migration `1.sqm` instead of regenerating the committed version-1 baseline. Verify it in
two ways: SQLDelight schema comparison and a runtime test that opens the populated version-1
fixture through the production desktop factory.

Persist queued and running states without replay semantics. A distinct interrupted state is
available for the later startup-recovery policy; this increment does not perform that transition.

## Alternatives considered

### Store one serialized transcript blob

Rejected because individual identities, constraints, ordering, migration, and future diagnostics
would become opaque to the database. Updating one field would require replacing and validating the
entire serialized format.

### Store run output only inside the attempt row

Rejected because message history and attempt provenance have different identities and lifecycles.
Regeneration must retain both previous message text and each attempt record without conflating the
two.

### Persist every streamed token immediately

Rejected because it creates excessive write amplification and still cannot guarantee that related
message and attempt fields are checkpointed together. A later coordinator will choose bounded,
useful checkpoint intervals and replace the aggregate atomically.

### Regenerate the version-1 schema fixture

Rejected because replacing historical evidence would allow a broken migration to appear valid.
The old fixture remains immutable and the numbered migration advances it.

## Consequences

- Transcript invariants fail before database work begins.
- Database constraints provide a second defensive layer against inconsistent state.
- A complete transcript replacement is simple and atomic, but can write unchanged rows; checkpoint
  scheduling must remain bounded.
- Correct deletion does not depend on platform-specific foreign-key initialization behavior.
- The schema can represent interrupted attempts, but startup recovery and automatic loading remain
  deliberately absent.
- Credentials, raw provider bodies, and transient presentation notices remain outside SQLite.
