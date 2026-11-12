# ADR 0050: iOS default data protection

## Status

Accepted.

## Context

Conversation history contains user content and SQLDelight may create SQLite journal, WAL, and shared
memory sidecars alongside the primary database. Protecting only one known filename would leave new
app-owned files dependent on implicit platform defaults.

## Decision

Enable the iOS Data Protection entitlement with `NSFileProtectionComplete` for both debug and
release builds. This app is foreground-first, so local data does not need to remain available while
the device is locked. The default applies at file creation, covering the native driver's database
and sidecars in its Application Support directory as well as future app-owned sensitive files.

Keep the entitlement in the XcodeGen source and generated Xcode project. CI validates the reviewed
entitlement value and confirms that both build configurations reference the same file before the
simulator launch gate.

## Consequences

- App-owned files are inaccessible while the iOS device is locked or booting.
- Background work must not assume database access after device lock.
- Simulator configuration evidence does not replace the later physical-device lock/unlock test.
- Explicit local-data deletion remains a separate operation with its own acceptance tests.
