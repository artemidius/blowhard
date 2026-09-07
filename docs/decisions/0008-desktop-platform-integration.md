# ADR 0008: Isolate desktop integration behind narrow boundaries

- Status: accepted
- Date: 2026-11-09

## Context

The shared conversation workflow must remain suitable for future mobile shells while the desktop
application needs operating-system integration. Clipboard access, menus, shortcuts, window state,
and application directories are platform concerns. Allowing those APIs into shared presentation
would couple reusable state and actions to AWT and make headless testing unreliable.

## Decision

- Keep clipboard writing as a small presentation port. Shared code owns the action and safe user
  feedback; the desktop composition root supplies an AWT adapter that fails closed when a graphical
  clipboard session is unavailable.
- Keep native menus and primary-key conventions in the desktop module. Menu items dispatch the same
  `ConversationAction` values as Compose controls instead of introducing a second workflow.
- Resolve configuration and cache directories from an explicit `DesktopEnvironment`. Native path
  types do not cross the desktop boundary, and tests cover spaces, Unicode, environment overrides,
  and platform fallbacks.
- Persist only bounded window dimensions and maximized state. Writes use a same-directory temporary
  file and atomic replacement when the filesystem supports it; malformed or inaccessible state
  falls back to safe defaults.
- Treat compact and wide presentation as an explicit layout decision. Keyboard focus, status live
  regions, control roles, labels, and content scrolling remain part of shared UI behavior.
- Exercise desktop adapters and the real desktop composition root on Linux, Windows, and macOS CI.
  Each native installer must complete the deterministic offline scenario after installation.

## Consequences

Shared presentation remains independent of AWT, filesystem layout, and operating-system names.
Desktop behavior can be tested with explicit environment values and replaceable native functions,
including headless failure paths. Future Android and iOS shells can provide their own clipboard
adapters while reusing the same presentation action.

The current persistence scope is deliberately small: conversation history and provider credentials
are not stored. CI proves package installation and deterministic execution, but it does not claim
interactive manual testing for every desktop environment, display server, input method, or assistive
technology.
