# ADR 0043: Shared mobile workspace shell

## Status

Accepted.

## Context

Android and iOS now assemble equivalent live application runtimes, but both hosts still expose only
the provider connection screen. Reusing the desktop root would also reuse desktop navigation and
would make mobile behavior an incidental compact-window mode.

## Decision

Introduce a dedicated shared mobile root. It observes the existing presenters, keeps the connection
gate authoritative, and owns mobile navigation independently from the desktop root. Chats,
Documents, and Settings are bottom-level destinations; detail screens temporarily hide the bottom
bar and return to their owning root.

Save only the validated route identifiers defined by the bounded navigation model. Keep this shell
unwired from production hosts until the dedicated mobile conversation surface is complete.

## Consequences

- Android and iOS will share navigation policy without sharing native lifecycle ownership.
- Provider verification still blocks the entire workspace when live mode is unavailable.
- Desktop navigation and layout remain unchanged.
- Mobile screens can be replaced incrementally before either host adopts the new root.
