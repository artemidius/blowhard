# ADR 0040: Android live generation composition

## Status

Accepted.

## Context

The Android host can verify a Keystore-backed credential and discover models, but its portable
runtime still uses a fail-closed generation stub. Chat presentation cannot safely progress until
the host supplies the reviewed live-only provider boundary without enabling unrelated document,
export, clipboard, or write capabilities.

## Decision

Add a conversation-only application dependency factory. It preserves explicit connection and text
generation ports while every optional capability retains its fail-closed implementation. Compose
the Android runtime with the shared live-only Mistral generation adapter and the same credential
provider used by model discovery.

Keep the connection-only Android surface until the dedicated mobile navigation increment is ready.
This makes provider composition independently reviewable and prevents a desktop workspace layout
from becoming the accidental mobile interface.

## Consequences

- Android generation uses the model captured by each immutable live attempt and never falls back
  to demo mode.
- Credential reads remain inside the Mistral adapter and Android vault boundary.
- Unimplemented mobile capabilities continue to reject operations explicitly.
- The provider runtime can be exercised by the upcoming mobile conversation UI without another
  host-composition change.
