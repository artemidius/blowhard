# ADR 0054: Android local-data reset composition

## Status

Accepted.

## Context

Android keeps its application runtime in a view model so activity recreation does not restart
generation or reopen storage. A destructive reset must replace that retained runtime, remove both
the Keystore-backed credential record and SQLite files, and return the UI to the connection gate.

## Decision

Represent one Android application session as the portable runtime plus its conversation history
session and provider composition. Publish the current session from the shared replaceable-session
owner. Compose observes that state and switches to the new runtime only after the old runtime and
native resources have closed, local data has been attempted, and the replacement is ready.

Use a dedicated Android eraser with injected credential storage and database name. Credential and
database deletion are attempted independently, so a vault failure cannot skip database cleanup.
The production composition supplies the app-scoped Keystore store and database. Device tests use a
recording credential store and uniquely named database, avoiding production credential aliases and
user data.

The reset presenter is owned by the view model rather than the replaceable session. It therefore
survives runtime replacement, reports a sanitized result, and remains available on the connection
gate after successful credential deletion.

## Consequences

- Activity recreation continues to retain one current session without replaying work.
- Successful reset publishes empty history and requires a new verified API key.
- Partial deletion is visible and retryable; native paths and errors never enter presentation state.
- View-model shutdown cancels reset presentation and closes whichever session is current.
