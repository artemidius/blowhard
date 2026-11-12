# ADR 0047: Shared conversation history session

## Status

Accepted.

## Context

Every application host must open durable conversation storage, interrupt uncertain attempts before
display, map the recovered snapshot, and release the database exactly once. Keeping this lifecycle
inside each platform would duplicate safety-critical startup behavior.

## Decision

Introduce an application-composition session that owns the conversation store and complete startup
recovery result. Opening or loading failures produce a generic, provider-inert unavailable state
and close any partially opened store. Repeated shutdown is idempotent and close failures cannot
prevent the rest of application shutdown.

Platform hosts remain responsible for choosing the database location and constructing the native
SQLDelight driver.

## Consequences

- Desktop, Android, and iOS can share one recovery and ownership policy.
- Recovery always precedes presenter construction and never replays provider work.
- Platform-specific paths and file-protection policy remain outside portable composition.
