# ADR 0037: Android live connection composition

## Status

Accepted.

## Context

The Android shell already renders the shared connection surface and the mobile platform adapter
provides app-scoped Keystore credential storage. Leaving the host on its bootstrap fail-closed
settings would make that secure adapter unreachable and would prevent startup credential
verification or model selection.

## Decision

Compose Android's connection boundary in the application host from the Keystore credential store,
the credential-only Mistral provider, the production model catalog, and
`DefaultConnectionSettings`. Start in live mode and enable automatic verification so a stored key is
tested at startup and a newly saved key is tested immediately. The application requests only normal
network access; credential material remains read just in time and never enters presentation state.

The retained Android `ViewModel` owns the model catalog and closes its HTTP client after the portable
application runtime stops. All generation and other provider operations remain fail-closed in this
increment. Enabling them requires separate composition and lifecycle evidence.

## Consequences

- A missing key leaves the application inactive and presents the secure input flow.
- Invalid, unavailable, or unauthorized connections remain visible without exposing credential
  material or silently falling back to demo behavior.
- Compatible models come only from a successful live catalog response and remain selectable through
  the shared connection API.
- The Android application host gains explicit dependencies on the mobile platform adapter, Mistral
  adapter, and application policy module.
