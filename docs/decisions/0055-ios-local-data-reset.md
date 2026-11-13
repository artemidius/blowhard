# ADR 0055: iOS local-data reset composition

## Status

Accepted.

## Context

The iOS host owns a Kotlin runtime behind one Compose view controller. A destructive reset must
replace that runtime, remove both the Keychain credential and SQLite files, and return the existing
surface to the connection gate without requiring SwiftUI to mirror application state.

## Decision

Represent one iOS application session as the portable runtime plus its conversation history session
and provider composition. Compose observes the shared replaceable-session owner and renders the
published session. The old runtime and native resources close before deletion begins; the clean
replacement is published only after deletion has been attempted.

Use a dedicated iOS eraser with injected credential storage and database name. Keychain and database
deletion are independent, so one failure cannot skip the other. Tests use a recording credential
store and a uniquely named database rather than production Keychain records or user data.

The reset presenter remains outside the replaceable session. It can therefore report a sanitized
outcome after replacement and remains usable from the post-reset connection gate.

## Consequences

- SwiftUI continues to host one controller while Compose reacts to session replacement.
- Successful reset publishes empty history and requires a newly verified API key.
- Partial deletion is visible and retryable without exposing native paths or errors.
- Controller disposal closes the reset presenter and whichever session is current.
- The iOS host binaries link the system SQLite library explicitly because reset integration tests
  exercise the native driver directly.
