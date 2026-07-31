# Blowhard

A desktop-first Kotlin Multiplatform client for Mistral AI.

Blowhard is in early development. The current foundation provides a shared Compose presentation
module for desktop, Android, and iOS, plus a dedicated desktop entry point. Application behavior
will be exposed through a platform-neutral core API in the next architecture increment.

## Technical direction

- Kotlin Multiplatform and Compose Multiplatform.
- Streaming conversations, document workflows, and controlled AI tool execution.
- Small, focused modules with explicit contracts and platform-specific adapters.
- Security-conscious design, automated testing, static analysis, and documented APIs.

The desktop application is the initial focus, with Android and iOS compatibility guiding
the shared architecture.

## Prerequisites

- JDK 21
- Android SDK 36 for Android compilation
- Xcode for iOS compilation; the shared UI targets iOS 14 and newer

The toolchain is pinned in the Gradle version catalog and documented in
[`ADR 0001`](docs/decisions/0001-toolchain-and-project-shape.md).

The shared client contract and enforced module direction are documented in
[`ADR 0002`](docs/decisions/0002-core-api-and-dependency-boundaries.md).

## Run the desktop application

```shell
./gradlew :apps:desktop:run
```

## Verify the foundation

```shell
./gradlew :apps:desktop:build :shared-ui:compileAndroidMain
./gradlew checkModuleBoundaries
```

On macOS, also verify the shared iOS target:

```shell
./gradlew :shared-ui:compileKotlinIosSimulatorArm64
```

Native desktop installers are built on their corresponding operating systems; cross-packaging is
not supported.

The application currently displays only the runnable project foundation. AI functionality and
release artifacts are not available yet.

Blowhard is an independent project and is not affiliated with Mistral AI.
