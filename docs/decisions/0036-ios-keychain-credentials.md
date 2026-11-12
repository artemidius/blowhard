# ADR 0036: Store iOS credentials in a device-bound Keychain item

## Status

Accepted.

## Context

The iOS application needs secure provider credentials without reusing the desktop JNA adapter, persisting
plaintext, or enabling iCloud synchronization. Provider calls happen in the foreground and may read the key
just in time, so storage must distinguish missing, locked, denied, and unavailable states without leaking native
details. iOS can preserve Keychain items across uninstall, which requires an explicit product policy.

## Decision

Implement the provider-neutral `CredentialStore` in the mobile-platform adapter with Security framework
generic-password operations. The application item has a fixed app-scoped service and account, does not request
an access group, explicitly excludes synchronizable items, and uses
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. This keeps the item unavailable while the device is locked,
prevents migration to another device, and excludes iCloud Keychain synchronization.

Replacement first updates the exact non-synchronizing item. If it is absent, the adapter adds one and resolves a
concurrent duplicate by updating again. Updates reapply the accessibility class. Delete is idempotent. All input
and output passes through the same bounded, single-line, strict UTF-8 policy as Android; temporary byte buffers
are cleared after use. Cancellation remains cancellation, while Security status codes map to sanitized domain
reasons rather than logs or exception text.

The reinstall policy is preservation: iOS may retain the Keychain item after application uninstall, and a
subsequent install can reuse it. Users can explicitly replace or delete it from connection settings. Blowhard
does not claim that uninstall erases the credential.

## Consequences

- Core, Mistral, and shared UI code contain no Security framework APIs.
- There is no plaintext, environment, database, macOS-JNA, or demo-provider fallback.
- `WhenUnlockedThisDeviceOnly` deliberately returns an interaction-required state while the device is locked.
- The adapter does not add biometric prompts; the existing device-unlock boundary matches foreground provider use.
- Deterministic tests cover validation, clearing, sanitized failures, cancellation, and lifecycle semantics. The
  Apple CI runner also executes save, read, replace, and delete against an isolated simulator Keychain identity.

## References

- [Keychain Services](https://developer.apple.com/documentation/security/keychain-services)
- [`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)
- [`SecItemCopyMatching`](https://developer.apple.com/documentation/security/secitemcopymatching(_:_:))
