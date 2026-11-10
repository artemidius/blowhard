# ADR 0015: Regenerate only as an explicit distinct attempt

- Status: accepted
- Date: 2026-11-10

## Context

A streaming POST may have produced partial output or incurred provider work before a failure becomes
visible. Recollecting an existing provider flow or silently resubmitting its prompt could duplicate
cost and output. At the same time, a user should be able to request a fresh answer without losing
the prior result or its source identity.

## Decision

- Expose regeneration as a user action on one terminal assistant message. Never dispatch it from a
  failure handler, timer, observer, or transport adapter.
- Associate each presented user/assistant pair with the immutable `RunId` returned by
  `ConversationRuns.start`. Render that attempt identity with both messages.
- Resolve the original prompt from the user message carrying the same attempt identity, then call
  `start` exactly once. The new run receives a new identity from the application layer.
- Append a new user/assistant pair. Do not edit, replace, or remove the prior attempt's prompt,
  output, delivery state, target, or identity.
- Snapshot routing again when the new run is accepted. Regeneration therefore uses the currently
  selected demo/live mode and verified model; it does not silently reuse or switch a target.
- Permit regeneration after success or cancellation. After failure, permit it only for
  `EXPLICIT_NEW_ATTEMPT` advice. `DO_NOT_RETRY` and `RETRY_AFTER_DELAY` do not expose the action in
  this increment.
- Reject duplicate actions while the conversation owns an active run. A rejected start restores
  the complete prior presentation state and preserves an unrelated composer draft.

## Consequences

Historical answers and provenance remain inspectable while every provider execution has a separate
application identity. The shared presenter remains the only component that translates the user
intent into a start; provider adapters still execute one request per collected generation flow and
perform no automatic retry.

The current history is process-local. Durable attempt storage, delayed-action countdowns, branching
conversation UX, and automatic retry remain outside this decision.
