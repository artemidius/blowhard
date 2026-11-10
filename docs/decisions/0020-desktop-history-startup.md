# ADR 0020: Desktop history startup boundary

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The desktop application previously constructed only in-memory presentation and run services even
though the shared persistence port, SQLDelight adapter, transcript schema, migrations, and recovery
operation existed. Enabling reads requires an explicit startup order and resource owner without
leaking SQLDelight, SQLite rows, or desktop filesystem types into shared presentation.

Storage can also be unavailable or corrupt. Silently substituting a disposable database would make
history loss difficult to detect, while exposing driver exceptions or local paths would leak
unnecessary diagnostics into the UI.

## Decision

Add `ConversationHistoryLoader` in the application layer. It performs fail-closed recovery before
listing conversations and loading their transcripts. It has no generation-port dependency and
therefore cannot contact or replay a provider during startup.

Let the desktop composition root create the platform configuration directory, open
`conversations.db`, own the resulting store for the runtime lifetime, and close it exactly once.
Map application history into existing presentation models at the composition boundary. The shared
presenter accepts validated initial state but remains independent from persistence and filesystem
types.

Select the first entry from the store's deterministic most-recent ordering. Use a clean demo
workspace for an empty database. If open, migration, recovery, or loading fails, show a generic
unavailable-history notice and keep durable storage disconnected; do not create a hidden fallback
repository. Never display exception text, database content, or local paths.

Reserve restored conversation and message identities in the presenter before generating new
in-memory identities. Give each desktop process a fresh opaque run-ID prefix so new attempts cannot
alias restored attempts from an earlier process. Present recovered interrupted attempts as
terminal, retain their partial output, and do not create run observers for them.

## Alternatives considered

### Query SQLDelight from the presenter

Rejected because presentation would depend on adapter rows and platform lifecycle. Mobile and
desktop clients must consume the same application-owned history policy through neutral models.

### Silently start with an in-memory repository after storage failure

Rejected because the user could mistake a disposable session for durable history. The degraded
state must be explicit and content-safe.

### Start provider observers for recovered active rows

Rejected because the previous process owned those executions and provider acceptance is uncertain.
Recovery marks them interrupted before any history becomes visible.

### Add checkpoint writes in the same change

Rejected because checkpoint cadence and write-failure policy were separate lifecycle decisions.
This increment proved read/recovery ownership first; bounded writes were subsequently adopted in
[`ADR 0021`](0021-bounded-run-checkpoints.md).

## Consequences

- Desktop startup displays durable history without provider traffic.
- SQLite ownership and cleanup are explicit and independently tested.
- Shared presentation remains reusable by future mobile composition roots.
- Corrupt or unavailable storage is visible without exposing sensitive diagnostics.
- Runtime checkpoint writing remains a separate application-lifecycle responsibility documented in
  [`ADR 0021`](0021-bounded-run-checkpoints.md).
