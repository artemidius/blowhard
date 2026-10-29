# ADR 0045: Native hosts adopt the shared mobile root

## Status

Accepted.

## Context

Android and iOS already own equivalent application runtimes, secure credential stores, and live
Mistral adapters. Their production hosts still render the temporary connection-only surface, so
the completed shared mobile workspace cannot be reached after credential verification.

## Decision

Pass every application presenter from each native runtime into `BlowhardMobileApp`. Keep runtime
construction and disposal native, while the shared root owns mobile navigation and connection
gating.

Apply safe drawing insets once at the shared mobile root. Disable nested Scaffold and navigation
bar inset consumption so Android edge-to-edge windows and iOS safe areas cannot accumulate double
padding.

## Consequences

- Android and iOS expose the same mobile workspace after successful credential verification.
- Secure platform credential stores and native lifecycle ownership remain unchanged.
- The no-credential launch path remains inactive and visibly requests an API credential.
- Desktop composition and window behavior remain unchanged.
