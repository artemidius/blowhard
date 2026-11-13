# ADR 0069: mobile accessibility semantics

## Status

Accepted.

## Context

Android and iOS render the same Compose mobile workspace. Visual hierarchy alone does not expose
screen structure to assistive technology, decorative glyphs can produce noise, and asynchronous
connection or operation results can change without moving focus.

## Decision

Expose each primary mobile screen title as a semantic heading, give the unlabeled conversation
composer and send action stable spoken names, and remove decorative navigation glyphs from the
accessibility tree while retaining the visible destination labels. Mark transient notices,
connection state, generation state, and diagnostic-export results as polite live regions.

Keep interaction targets on Material controls so the platform minimum target policy remains in
force. Validate the real Android startup surface on an emulator by asserting its heading and live
region semantics; compile the same shared UI for iOS in CI.

## Consequences

- TalkBack and VoiceOver receive the same portable screen hierarchy and status announcements.
- Decorative symbols no longer compete with meaningful navigation labels.
- Semantic assertions protect the secure startup gate from silent regressions.
- This evidence is a focused automated accessibility check, not a claim of certification or a
  complete manual assistive-technology audit.
