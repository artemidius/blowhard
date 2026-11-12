# ADR 0035: Protect Android credentials with a Keystore-backed envelope

## Status

Accepted.

## Context

Android Keystore stores cryptographic keys, not arbitrary API-key text. Persisting the provider credential directly in
preferences, a database, a source constant, or an environment variable would create a plaintext fallback and violate the
existing `CredentialStore` boundary. Credential data must also remain outside cloud backup and device transfer.

## Decision

Add a Kotlin Multiplatform mobile-platform adapter with an Android implementation. It generates a non-exportable 256-bit
AES key in Android Keystore and authorizes only randomized GCM encryption and decryption. Each replacement receives a new
provider-generated IV and authenticates an application-specific context value. The app-private preferences file contains
one versioned record with only the IV and ciphertext; the manifest's existing backup rules exclude all preferences from
cloud backup and device transfer.

The adapter validates a bounded, single-line UTF-8 credential before encryption and clears temporary byte arrays after
use. Missing records remain distinct from corrupt records, missing keys, cryptographic failures, and persistence failures.
All unsafe outcomes map to sanitized domain results, never exception text or credential material. A key explicitly
reported as permanently invalidated is rotated only during an intentional replacement; reads never discard data or
silently create a key.

The store does not require biometric or lock-screen authentication for each access. That interaction would conflict with
just-in-time foreground provider reads and is not needed for the current threat model. Android Keystore still prevents the
AES key from being extracted as application data, but it does not protect credentials from a fully compromised device or
process.

## Consequences

- Android storage implements the provider-neutral `CredentialStore`; core and Mistral modules contain no Android APIs.
- There is no plaintext compatibility path or automatic provider fallback.
- Backup exclusion is defense in depth: ciphertext is unusable without the device-bound Keystore key, and it is excluded
  from transfer altogether.
- Host tests cover validation, clearing, corruption, cancellation, sanitization, and invalidated-key recovery. An emulator
  test exercises real Keystore generation, replace, read, corruption recovery, and deletion with isolated test identities.
- iOS can reuse the common validation policy while supplying a native Keychain implementation.

## References

- [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography)
- [Android `KeyGenParameterSpec`](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)
- [Android backup security recommendations](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
