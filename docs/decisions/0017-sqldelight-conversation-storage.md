# ADR 0017: SQLDelight conversation storage boundary

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Conversation history must survive process restarts on desktop without coupling the shared UI or
application rules to one storage engine. The same core contracts must remain viable for Android and
iOS. Schema changes need reviewable migrations and tests against real database files, while
credentials must stay outside ordinary application storage.

The existing in-memory run repository intentionally owns active execution. Replacing it directly
with database calls would mix provider lifecycle, persistence scheduling, and recovery policy.

## Decision

Define `ConversationStore` and its validated metadata record in `core:domain`. Implement the port in
a dedicated `adapters:persistence` Kotlin Multiplatform module using SQLDelight 2.4.1 and SQLite.
Compile JDBC, Android, and Native drivers against the pinned project toolchain. Expose small
platform factories. Generated database types remain an adapter implementation detail and are
excluded from its maintained ABI baseline; production code accesses them only through the private
store implementation.

Commit the generated version-1 schema baseline and run SQLDelight migration verification in the
aggregate quality gate. Target the conservative SQLite 3.18 dialect. Perform compatible saves as
an atomic insert-or-update transaction rather than requiring newer UPSERT syntax.

Keep the adapter inactive in the desktop composition root until messages, attempts, recovery, and
retention behavior have explicit contracts. Credentials remain exclusively in the credential
adapter.

## Alternatives considered

### Persist presentation models directly

Rejected because UI state includes transient notices, composer text, and rendering-specific
identities. It would make schema evolution follow presentation refactors and prevent a headless
client from using the same history.

### Use platform-specific persistence APIs

Rejected for the first implementation because Room, Core Data, and desktop SQLite would require
multiple schemas and migration systems before they provide distinct product value.

### Serialize conversations as JSON files

Rejected because multi-record updates, indexed ordering, migrations, and partial-write recovery
would become custom infrastructure. A database supplies these properties with less bespoke code.

### Wire persistence into run execution immediately

Rejected for this increment. Proving the driver and migration tuple independently keeps active-run
semantics unchanged and makes the storage boundary reviewable.

## Consequences

- SQL and migrations are compiled and checked rather than assembled dynamically.
- Desktop round-trip behavior is tested with real temporary SQLite files.
- Android and iOS driver compatibility is enforced at compilation now, before mobile hosts exist.
- The dependency graph and verification metadata grow with SQLDelight and its platform drivers.
- Local conversation content will rely on OS file protections unless a separately verified
  encryption design is introduced.
- Durable user-visible history remains unavailable until transcript and recovery increments adopt
  this port.

## References

- [SQLDelight overview](https://sqldelight.github.io/sqldelight/)
- [SQLDelight migration verification](https://sqldelight.github.io/sqldelight/latest/android_sqlite/migrations/)
