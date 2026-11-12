# ADR 0032: exact consent and no-replay local writes

## Status

Accepted.

## Context

A useful AI workflow must sometimes cross from proposal into mutation. A generic confirmation flag is insufficient:
content can change between presentation and execution, consent can be reused, and interruption can leave the caller
unable to distinguish a committed write from a failed one.

## Decision

Bind each approval to an immutable run ID, approval ID, call ID, tool name, complete serialized arguments,
human-readable destination, and expiry. Show the exact note content and destination before the user approves or
denies. Consume consent before invoking the mutation and never accept it again.

For the approved meeting-note copy, persist the note and deterministic operation receipt in one SQLDelight
transaction. On an uncertain or interrupted result, query the receipt and stored note. Recovery observes existing
state; it never repeats the mutation automatically.

## Consequences

- Denial and expiry write nothing; altered or replayed bindings fail closed.
- A committed receipt makes interruption recovery deterministic and idempotent.
- Storage unavailability remains explicitly uncertain instead of being reported as success.
- The UI depends on a portable approval port while platform composition owns identity, time, and persistence.
- Pending approvals are process-local and intentionally disappear on restart; restart cannot authorize a mutation.
