# ADR 0063: Immutable meeting-note capability composition

## Status

Accepted.

## Context

Platform hosts assemble optional document and meeting-note capabilities after their native stores are
opened. Mutating an already published dependency graph would make reset and lifecycle ownership hard
to reason about.

## Decision

Add `withMeetingNotes` to `ApplicationRuntimeDependencies`. It returns a new graph containing the
provided generation and approval boundaries while preserving conversation, document, diagnostics,
and platform services by identity.

## Consequences

- Mobile composition can add notes only after all required native resources exist.
- Session replacement publishes a complete immutable graph.
- Tests assert that capability composition cannot accidentally discard previously configured ports.
