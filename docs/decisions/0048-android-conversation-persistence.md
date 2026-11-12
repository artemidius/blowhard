# ADR 0048: Android conversation persistence

## Status

Accepted.

## Context

The Android host currently creates an in-memory conversation workspace on every launch even though
the portable runtime already checkpoints through `ConversationStore` and the persistence adapter
already supplies an Android SQLDelight driver.

## Decision

Open one app-private SQLDelight conversation store before constructing the Android runtime. Recover
uncertain attempts through the shared history session, use its snapshot as initial UI state, and
inject the same owned store into conversation runs for future checkpoints.

The Android application remains the platform composition root, so it may construct the concrete
persistence adapter just as the desktop application does. Portable application composition still
depends only on core contracts and shared presentation.

Close generation first, then history storage, then provider clients. Keep the existing complete
cloud-backup and device-transfer exclusions; the database is local-only and contains no API key.

## Consequences

- Completed Android conversations and transcripts survive process restart.
- Queued or running attempts become interrupted at startup and are never replayed automatically.
- Storage failure is visible as an unavailable-history notice while live generation remains usable
  for the current non-durable session.
- Database deletion and full relaunch validation remain separate follow-up work.
