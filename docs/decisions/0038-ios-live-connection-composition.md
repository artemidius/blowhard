# ADR 0038: iOS live connection composition

## Status

Accepted.

## Context

The iOS shell embeds the shared Compose connection surface and has a device-bound Keychain
credential adapter. The adapter remains unreachable while the host uses its bootstrap fail-closed
connection settings, so iOS cannot save a key, verify it at startup, or discover selectable models.

## Decision

Compose the iOS connection boundary in the exported application host from the Keychain credential
store, the credential-only Mistral provider, the production model catalog, and
`DefaultConnectionSettings`. Start in live mode and enable automatic verification. The resulting
policy matches Android without moving platform composition into shared UI.

The controller-owned runtime owns the model catalog. Disposing the Compose controller stops the
portable runtime before closing the catalog's HTTP client. Other provider operations stay
fail-closed until their own composition increments provide lifecycle and platform evidence.

## Consequences

- The iOS application stays inactive when no credential exists and never falls back to demo mode.
- Saved credentials are tested at startup and after replacement; failures remain visible and
  redacted.
- Model choices are populated only after a successful live catalog response.
- The iOS host gains explicit dependencies on the mobile platform adapter, Mistral adapter, and
  application policy module.
