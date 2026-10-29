# ADR 0046: Shared history presentation

## Status

Accepted.

## Context

Conversation recovery and durable transcript models are portable, but their mapping into UI state
was owned by the desktop application. Mobile hosts need identical interruption, retry, and
no-replay semantics before they can connect the existing SQLDelight stores.

## Decision

Move durable-history presentation mapping into the shared presentation layer. Preserve the exact
ordering, delivery, regeneration, and interruption-notice rules. Expose deterministic empty and
storage-unavailable states for every host.

The mapping remains provider-inert: it transforms an already recovered snapshot and cannot start,
resume, or replay generation.

## Consequences

- Desktop and mobile hosts use one history-to-UI policy.
- Recovery semantics are covered by common multiplatform tests.
- Platform composition still owns database opening, failure handling, and resource closure.
