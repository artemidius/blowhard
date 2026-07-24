# ADR 0001: Toolchain and initial project shape

- Status: accepted
- Date: 2026-11-09

## Context

Blowhard needs one shared presentation layer that can support desktop and mobile clients while
keeping operating-system entry points and packaging concerns at the edge. Desktop deliverables
must run on macOS, Windows, and Linux.

The initial build also needs a reproducible, mutually compatible Kotlin, Compose, Android, Gradle,
and Java toolchain.

## Decision

Use the following pinned foundation:

- Kotlin 2.4.21, Compose Multiplatform 1.12.2, and Material 3 1.9.0;
- Gradle 9.7 with its wrapper distribution checksum;
- Android Gradle Plugin 9.3.1 and the Android-KMP library plugin;
- JDK 21 for Gradle and desktop compilation, with JVM 17 Android bytecode;
- Android compile SDK 36 and minimum SDK 26;
- iOS 14 on ARM64 devices and ARM64 simulators as the initial mobile compatibility baseline;
- macOS 13 on Apple silicon, Windows 10 x86-64, and Ubuntu 20.04 x86-64 as the initial desktop
  compatibility baselines.

The `shared-ui` module contains Compose code compiled for Android, desktop JVM, and iOS. The
`apps:desktop` module owns only the desktop process lifecycle and native distribution settings.
Future Android and iOS application modules will provide their own entry points.

## Consequences

- Shared presentation code cannot rely on desktop-only APIs.
- Desktop packaging remains isolated from mobile compilation.
- Native desktop installers must be built on their corresponding operating systems.
- Compose Multiplatform no longer publishes Apple x86-64 UI artifacts, so Intel iOS simulator
  targets are intentionally excluded.
- The compatibility baselines can be raised deliberately, but changes require recorded validation
  and a new decision.
