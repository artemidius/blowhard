# ADR 0022: Confirmed local conversation deletion

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The persistence adapter could atomically delete a conversation and its owned transcript rows, but
no client-facing application operation or presentation action exposed that guarantee. Letting a
presenter call the storage port directly would bypass active-run coordination and couple shared UI
to persistence. Removing only a list item would also imply deletion without proving durable data
was removed.

Deletion must be truthful about its scope. Blowhard owns its local SQLite history, but it does not
control copies already sent to Mistral, filesystem backups, or secure erasure. Provider credentials
have a separate platform-vault lifecycle and must never be affected by conversation deletion.

## Decision

Add a provider-neutral `ConversationLibrary` contract with typed deletion outcomes. Implement it
in the application lifecycle that already owns run coordination and durable checkpoints. Reserve
the conversation identity before storage access: an active run or another deletion rejects the
operation, and a new run for that identity cannot start until deletion finishes.

Only `DELETED` and `NOT_FOUND` allow presentation to remove the item. `NOT_FOUND` supports a newly
created workspace that has never been persisted. Storage failures and unavailable storage keep the
conversation visible and return safe feedback. A successful or absent-row result discards retained
application run lookups for that conversation.

Require an explicit shared-UI confirmation. The dialog names the conversation and states that the
operation removes local messages and run history without changing Mistral retention or the API
credential. Active conversations cannot open the dialog. Deleting the final item creates a fresh
empty workspace whose identity does not collide with restored state; it is not persisted until a
run is accepted.

Retain conversations indefinitely until this explicit per-conversation operation. Do not add an
automatic expiry window or bulk delete in this increment.

## Alternatives considered

### Delete directly from the presenter

Rejected because presentation would depend on a storage implementation and could race generation.
Headless and future mobile clients also need the same application-owned rule.

### Cancel an active run automatically, then delete

Rejected because cancellation and deletion are distinct destructive intentions. Requiring the user
to stop first keeps partial-output handling observable and avoids an ambiguous combined operation.

### Remove the item optimistically and restore it after failure

Rejected because storage failure could make restored presentation diverge from concurrent state.
The item stays visible until durable deletion is confirmed.

### Delete provider-side data or the credential at the same time

Rejected because those resources have different ownership, APIs, and security consequences. The
confirmation must not imply capabilities Blowhard does not have.

## Consequences

- Desktop users can verify complete local conversation deletion and restart with no restored row.
- Run start and deletion are serialized per conversation without holding a lock during SQLite I/O.
- Storage ambiguity fails closed and never produces a false success state.
- Desktop, Android, iOS, and headless clients can reuse the same typed deletion contract.
- Local database deletion is not secure erasure and does not remove backups or provider copies.
- Bulk deletion and time-based retention remain future, separately reviewable policies.
