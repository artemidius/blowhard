# ADR 0049: iOS conversation persistence

## Status

Accepted.

## Context

The iOS host currently creates a fresh in-memory conversation on every launch even though the
portable runtime already checkpoints through `ConversationStore` and the persistence adapter
supplies an Apple-native SQLDelight driver.

## Decision

Open one application-owned native SQLite conversation store before constructing the iOS runtime.
Recover uncertain attempts through the shared history session, use its snapshot as initial UI
state, and inject the same owned store into conversation runs for future checkpoints.

The iOS shared application is the platform composition root, so it may construct the concrete
persistence adapter. Portable application composition continues to depend only on core contracts
and shared presentation.

Because the exported Kotlin framework is static, the native Xcode host links Apple's system
SQLite library explicitly; the framework cannot carry that final application-link responsibility.

Close generation first, then history storage, then provider clients. Storage failures remain
fail-closed and visible through the shared unavailable-history state while the current session can
continue without durability.

iOS database file protection and explicit local-data deletion are separate focused changes. This
decision does not claim those policies are complete.

## Consequences

- Completed iOS conversations and transcripts survive process restart.
- Queued or running attempts become interrupted at startup and are never replayed automatically.
- The native store remains private to the application and follows one explicit ownership chain.
- Hosted simulator build and launch evidence validates the concrete application integration.
