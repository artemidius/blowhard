# ADR 0058: Compose mobile capabilities without widening host ownership

## Status

Accepted.

## Context

Mobile hosts start with a fail-closed conversation graph and add native capabilities in focused
increments. Reconstructing the entire dependency object in each host would duplicate unavailable
ports, obscure which capability became active, and make Android and iOS drift.

## Decision

Add immutable capability composition to `ApplicationRuntimeDependencies`. `withDocuments` replaces
the document library, OCR service, native picker, and processing store as one coherent set while
preserving every conversation, settings, inspection, and meeting-note dependency by identity.

Platform hosts still create and own native stores, provider clients, and pickers. The portable
composition root receives only interfaces and does not learn Android, UIKit, filesystem, or
transport details.

## Consequences

- Mobile features can be activated through small, reviewable dependency changes.
- A partially wired document feature cannot accidentally mix live and unavailable sub-capabilities.
- Android and iOS keep native resource construction and closure at their application boundaries.
- Future capability groups can follow the same immutable composition pattern.
