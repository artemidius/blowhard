# ADR 0012: Require explicit, verified live-provider settings

- Status: accepted
- Date: 2026-11-10

## Context

The shared application needs to configure live generation without exposing provider credentials to
presentation code or treating a successful secret-store write as proof that a provider is usable.
Model identifiers and capabilities can change independently of the installed client, and an
omitted capability must not accidentally enable an unsupported workflow.

The settings contract must also preserve a clear distinction between the deterministic offline
demo and external processing. A failed connection check must not silently change the user's chosen
mode, and entering live mode requires acknowledgement that prompt data leaves the device.

## Decision

- Define a provider-neutral `ConnectionSettings` API in `core:api`. Its observable state contains
  credential status, connection status, compatible model metadata, and explicit mode/disclosure
  choices, but never credential material.
- Implement orchestration in `core:application` using the existing `CredentialStore` and
  `ModelCatalogPort`; presentation and provider modules remain independent.
- Test connectivity with the non-generative model catalog. A model is selectable only when the
  provider explicitly reports it as available and explicitly supports chat completion. Unknown or
  absent values fail closed.
- Require a configured credential, a successful current check, a selected compatible model, and a
  recorded data-processing acknowledgement before live mode can be selected.
- Serialize settings mutations so credential changes, catalog results, and selections cannot
  overwrite one another. Restore the previous observable state when a check is cancelled.
- Invalidate model and connection evidence after credential replacement or deletion. A failed
  re-check marks the connection stale but does not silently switch an existing live selection to
  demo mode.
- Expose only sanitized, provider-neutral result categories. Provider payloads, native credential
  errors, and exception messages do not enter settings state.

The compatibility check follows Mistral's documented
[`GET /v1/models`](https://docs.mistral.ai/api/endpoint/models) contract, while the public settings
API remains independent of Mistral and its transport types.

## Consequences

Desktop and future mobile interfaces can use one stable settings workflow and render explicit,
testable states. Model selection is based on current provider evidence instead of a hard-coded
identifier, and UI code does not need access to secrets, Ktor, or Mistral response objects.

The stricter compatibility rule can hide a model when provider metadata is incomplete. This is an
intentional security and correctness trade-off. A stale live selection cannot be used for a new
request until routing verifies that the current state is ready.

This increment does not add settings UI, connect the selected model to conversation generation, or
implement Windows and Linux credential vaults. Those remain separate, reviewable changes.
