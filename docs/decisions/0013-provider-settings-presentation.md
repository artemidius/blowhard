# ADR 0013: Keep provider-settings presentation secret-free

- Status: accepted
- Date: 2026-11-10

## Context

The desktop client now needs to expose the shared connection-settings workflow without giving
Compose state ownership of a persisted credential or allowing provider and platform types to leak
into shared presentation. The current conversation workflow remains an explicitly offline fixture;
configuration must not imply that chat has already been rerouted to the live provider.

The desktop composition root is also the first client that must select both a provider adapter and
an operating-system credential adapter. This is the responsibility assigned to client composition
roots by ADR 0002, but those dependencies were not needed by the earlier offline-only build.

## Decision

- Add a shared `ConnectionSettingsPresenter` that depends only on the provider-neutral
  `ConnectionSettings` contract. Its observable state contains the core secret-free snapshot and
  sanitized feedback strings.
- Keep credential text in ordinary, non-saveable Compose state only while the field is visible.
  Clear the field immediately when submitting, redact the internal action's diagnostic string, and
  pass the value directly to secure storage without copying it into presenter state.
- Render one adaptive settings screen for desktop and future mobile clients. It exposes vault
  status, replace/delete actions, a non-generative connection test, conservatively verified model
  selection, and the external-processing acknowledgement.
- Keep the conversation workspace visibly labeled `Offline Demo`. This increment reports live
  readiness but does not expose a live-mode switch or route a message to Mistral.
- Compose `DesktopCredentialStores`, `MistralModelCatalog`, `DefaultConnectionSettings`, and shared
  presentation in `apps:desktop`. The desktop runtime owns and closes the catalog.
- Adapt `CredentialStore` to `MistralCredentialProvider` with a just-in-time read. Missing or
  unavailable storage maps to no provider credential; the bridge does not cache the value.
- Expand the enforced desktop dependency allowlist to the Mistral and platform adapters. Core and
  shared presentation dependency rules remain unchanged.

## Consequences

The desktop app can configure and verify Mistral on macOS without exposing a key in application
state, logs, diagnostics, or repository files. Windows and Linux render the secure-store unavailable
state and remain offline; there is still no plaintext fallback.

The settings presenter and UI compile for Android and iOS because they consume only the shared
contract. Mobile composition can provide its own verified credential adapter later without changing
the screen API.

Live conversation routing, mode-specific conversation identity, and supported generation controls
remain a separate change. Until that change lands, the application title, badges, composer, and
packaging smoke test continue to assert offline fixture behavior.
