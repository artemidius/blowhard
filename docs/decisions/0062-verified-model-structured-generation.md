# ADR 0062: Verified-model structured generation routing

## Status

Accepted.

## Context

Meeting-note generation must follow the model selected by the latest successful connection check.
Android, iOS, and desktop must not copy credentials into UI state or silently use a stale model after
the user changes settings.

## Decision

Keep model selection in `ConnectionSettings` and add a provider-neutral router in application
composition. The router creates one owned structured-generation adapter per verified model, reuses it
for subsequent requests, and rejects work before live prerequisites are satisfied. Closing the router
closes all adapters and permanently rejects new requests.

Platform composition roots supply the provider-specific adapter factory. The shared router depends
only on domain ports and secret-free connection state.

## Consequences

- Mobile hosts can support structured meeting notes without duplicating routing policy.
- Changing the verified model changes subsequent structured requests deterministically.
- Credentials remain inside provider adapters.
- Unit tests can verify routing and ownership without network access.
