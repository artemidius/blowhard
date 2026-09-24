# ADR 0014: Snapshot and route live generation without fallback

- Status: accepted
- Date: 2026-11-10

## Context

Provider settings can change while an accepted attempt is queued or streaming. Reading the current
mode only when execution begins could therefore send a prompt to a different source than the one
shown when the user pressed Send. A live failure must also never cause the same prompt to be
replayed automatically through the deterministic fixture.

The shared run lifecycle already owns validation, concurrency, cancellation, observation, and
terminal states. Live integration should reuse that lifecycle instead of creating a second path in
desktop presentation code.

## Decision

- Add a provider-neutral `GenerationTarget`: either `Demo` or `Live(modelId)`.
- When `DefaultConversationRuns` accepts a start, resolve the current settings once. Demo mode
  captures `Demo`; live mode captures the currently verified model only when every live-readiness
  invariant still holds.
- Reject stale live configuration before allocating or invoking provider work. Never substitute
  the demo target.
- Store the target with the run record and include it in the internal generation request. Later
  settings changes do not mutate an accepted attempt.
- Route both targets through one `ConversationRuns` lifecycle. The desktop composition root selects
  either the fixture adapter or a model-specific Mistral adapter from the immutable target.
- Cache one live adapter per selected model for the desktop process. Credentials are still read
  just in time by the adapter and are never added to the target, run state, or presentation state.
- Close run execution before closing cached provider clients, so active streams are cancelled and
  joined before their transport resources are released.
- Label the application title, conversation, composer, and assistant messages with their explicit
  source. A live transport or construction failure remains a live failure and never reaches the
  fixture adapter.

## Consequences

The same provider-neutral run API now executes deterministic and Mistral-backed attempts. Source
selection is reviewable, testable, and stable across queuing, while presentation can distinguish
historical fixture and live output.

Changing settings affects only future attempts. If a live connection becomes stale, the UI keeps
the explicit live selection visible and new starts fail closed until verification is restored.
Windows and Linux remain demo-only because their secure credential adapters are unavailable.

Regeneration, supported generation parameters, durable history, and a bounded real-provider smoke
record remain separate increments. This decision adds no automatic retry or replay behavior.
