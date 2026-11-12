# ADR 0033: dedicated mobile application hosts

## Status

Accepted.

## Context

Blowhard's portable contracts, application policy, adapters, and Compose UI already compile for Android and Apple
targets. A mobile product still needs platform-owned entry points, lifecycle integration, secure credential storage,
and distribution metadata. Hiding those responsibilities inside shared UI would couple portable presentation code to
Android or iOS and make each platform harder to test independently.

## Decision

Give Android and iOS dedicated application hosts. Each host may depend on portable contracts and shared Compose UI,
but shared modules never depend on an application host. Platform composition owns lifecycle, secure storage,
permissions, network engines, database drivers, and system integrations.

The first Android increment exposes only the connection screen through a narrow shared Compose entry point. Until an
Android Keystore-backed credential adapter is present, its host adapter reports credentials as unavailable and all
sensitive operations fail closed. No placeholder token, implicit demo mode, or network permission is introduced by
the shell.

The Android build targets the current API while retaining the project's established minimum API. Cloud backup and
device transfer are disabled for all app-owned data. Android application assembly, lint, unit tests, and a managed-device
launch assertion run in CI.

The iOS host is a small SwiftUI application embedding one Compose `UIViewController`. A separate KMP composition
module exports that controller factory through the static `BlowhardIos` framework; Swift does not receive the wider
shared UI API. The initial iOS 15 host also reports credentials as unavailable. CI builds the Xcode project without
signing, installs it on an available iPhone simulator, launches it, and verifies that the process remains alive.

## Consequences

- Mobile lifecycle and security decisions remain visible at the application boundary.
- Android and iOS can evolve their navigation without duplicating domain or presentation policy.
- A compilable shell cannot be mistaken for a live provider integration.
- Platform composition grows in small, reviewable increments as secure adapters become available.
