# ADR 0041: iOS live generation composition

## Status

Accepted.

## Context

The iOS host can verify a Keychain-backed credential and discover models, while its portable
runtime still rejects every generation attempt. Android now assembles the reviewed live-only
generation boundary through the conversation-only application dependency graph. iOS needs the
same policy without duplicating routing or resource-lifetime rules.

## Decision

Compose the iOS runtime with the shared live-only Mistral generation adapter. Reuse the credential
provider already supplied to model discovery and provide both ports through the conversation-only
dependency factory. Keep every unrelated optional capability fail-closed.

Retain the connection-only Compose surface until the dedicated mobile navigation increment. The
controller continues to close the portable runtime before the long-lived model catalog; generation
sessions remain request-bounded and close themselves.

## Consequences

- Android and iOS now share live-generation routing, model binding, cancellation, and failure
  redaction semantics.
- iOS never installs a fixture provider or demo fallback.
- Credentials remain inside the Keychain store and credential-provider boundary.
- The next mobile UI increment can expose conversation behavior without changing iOS provider
  composition.
