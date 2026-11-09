# ADR 0011: Store desktop credentials in native platform vaults

- Status: accepted
- Date: 2026-11-09

## Context

Live Mistral requests need a user-supplied API key. Keeping that value in preferences, SQLite,
environment variables, command-line arguments, or repository files would expose it outside the
operating system's credential protections. The desktop application currently verifies three hosts,
but only macOS has a credential integration implemented and exercised in this increment.

Apple documents generic-password items as encrypted Keychain records identified by service and
account attributes. Reads, updates, and deletion use the Security framework's `SecItem*` functions.
Those calls are blocking, so the adapter executes them on an I/O dispatcher rather than the UI
thread.

## Decision

- Define a provider-neutral `CredentialStore` port in `core:domain` with typed read, replace, and
  delete results. Diagnostics redact available secrets by construction.
- Implement the reference desktop adapter with macOS Keychain generic-password items. Use the
  application bundle identifier as the service and a fixed provider-specific account.
- Access Security and Core Foundation through JNA 5.19.1. JNA is dual-licensed under Apache-2.0 or
  LGPL-2.1-or-later; this project consumes it under Apache-2.0.
- Update an existing item first, add when missing, and resolve an add race with one final update.
  Deleting a missing item is successful and idempotent.
- Map native status values to a small sanitized failure taxonomy. Do not include status text,
  credentials, or native payloads in public results.
- Reject blank, whitespace/control-containing, or oversized values before storage. Clear temporary
  native byte buffers where the JVM and JNA APIs permit; Java `String` values cannot be reliably
  zeroized and therefore must not be retained or logged.
- Fail closed on Windows and Linux until their OS-backed vault adapters have separate verification.
  There is no file or preferences fallback.

## Consequences

The application can now safely own credential lifecycle independently of the Mistral transport.
Unit tests cover denial, locked/unavailable storage, replacement races, validation, byte clearing,
and idempotent deletion. An opt-in host test performs an isolated add/read/delete lifecycle using a
synthetic item in the real macOS Keychain.

This increment does not add credential-entry UI, connect the store to live requests, claim Windows
or Linux live support, or solve protection against malware running as the same OS user. Packaged-app
identity and user-facing lifecycle verification remain release gates.
