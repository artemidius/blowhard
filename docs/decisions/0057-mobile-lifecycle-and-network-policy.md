# ADR 0057: Mobile lifecycle and network policy

## Status

Accepted.

## Context

Mobile surfaces are recreated for reasons that do not end the application session: rotation,
window resizing, SwiftUI updates, and temporary backgrounding. Reconstructing the provider client,
presenters, or database for those events could duplicate work or lose an in-flight snapshot.
Transient network loss must likewise remain a typed attempt outcome rather than trigger a hidden
retry with another provider request.

## Decision

Keep Android application sessions in an `AndroidViewModel`; activity recreation only rebinds Compose
to the retained state flow. Keep the iOS session owned by the exported Compose view controller;
SwiftUI updates do not mirror or reconstruct Kotlin state. Closing the platform owner closes active
work, provider clients, and stores through the shared session boundary.

Do not cancel a run merely because the app becomes temporarily inactive. The structured application
scope continues until explicit user cancellation or owner shutdown, while durable checkpoints make
process death recoverable. Do not automatically retry a failed network request. Mistral adapters map
transport loss to sanitized unavailable outcomes, and users explicitly start any later attempt.

Validate the policy with an Android configuration-recreation device test, iOS application launch
verification, shared runtime shutdown tests, and adapter tests that prove cancellation propagation,
redacted network failures, and zero automatic retries.

## Consequences

- Rotation and transient surface recreation do not restart model calls or reopen storage.
- Temporary backgrounding can finish a user-started request while the process remains alive.
- Owner shutdown still cancels structured work and releases every native resource.
- Network interruption is visible, sanitized, and never silently replayed.
