# ADR 0039: Live generation session lifecycle

## Status

Accepted.

## Context

Mobile hosts need live Mistral chat generation after credential verification and model selection.
The existing desktop router also supports fixture scenarios and retains one HTTP client per model,
which is unnecessary for the mobile live-only boundary and would expose an accidental demo path.

## Decision

Provide a common Mistral adapter that accepts only immutable live generation targets. Create one
platform HTTP session when its flow is collected and release that session when the attempt
completes, fails, or is cancelled. Reject demo targets before creating provider resources. Redact
session-construction failures and prevent cleanup failures from replacing an already emitted
provider result.

Mobile hosts will share this adapter. UI and presentation code continue to depend only on the core
text-generation contract and remain unaware of Mistral or platform HTTP engines.

## Consequences

- Android and iOS receive identical live-only routing and resource semantics.
- Model changes require no retained per-model client cache or cache invalidation policy.
- Each attempt pays the cost of a new HTTP client in exchange for bounded, explicit ownership.
- Cancellation closes the active client and propagates without automatic retry or provider
  fallback.
