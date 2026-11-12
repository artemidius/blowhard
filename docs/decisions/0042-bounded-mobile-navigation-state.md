# ADR 0042: Bounded mobile navigation state

## Status

Accepted.

## Context

Desktop navigation is a window-local destination switch. Mobile clients need bottom-level
destinations, system back behavior, and recreation without retaining credentials or an unbounded
route history. The mobile conversation, document, and settings surfaces also own different detail
screens.

## Decision

Model mobile navigation as one selected root plus at most one root-owned detail. Selecting Chats,
Documents, or Settings clears transient detail state. Back removes a detail and is a no-op at a
root so the native host remains responsible for application exit.

Persist only stable, non-sensitive route identifiers. Restoration rejects unknown roots and drops
details that do not belong to the restored root.

## Consequences

- Navigation cannot accumulate an unbounded back stack.
- Run inspection stays under Chats and meeting-note review stays under Documents.
- UI recreation restores location without serializing presenters, content, or credentials.
- The navigation model is deterministic and testable without a Compose runtime.
