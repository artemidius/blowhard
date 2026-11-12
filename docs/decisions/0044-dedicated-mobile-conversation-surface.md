# ADR 0044: Dedicated mobile conversation surface

## Status

Accepted.

## Context

The desktop conversation screen falls back to a narrow layout, but that layout still exposes a
horizontal desktop navigation strip, keyboard-shortcut guidance, and automatic input focus. On a
phone this duplicates the mobile navigation bar, opens the software keyboard too early, and makes
conversation selection difficult.

## Decision

Render mobile conversations through a dedicated shared surface. Keep conversation selection in its
own bounded detail, show the active model in a compact header, retain send/stop/regenerate and safe
Markdown behavior, and apply IME padding to the composer. Do not request input focus automatically
on mobile.

The mobile surface reuses the existing presenter actions and content components; desktop behavior
and shortcuts remain unchanged.

## Consequences

- Phones no longer render the desktop compact navigation strip.
- The software keyboard appears only after explicit user interaction.
- Composer state, cancellation, deletion confirmation, and run inspection keep one shared policy.
- Android and iOS receive the same mobile conversation behavior when their hosts adopt the shell.
