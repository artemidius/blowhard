# Blowhard

A desktop-first Kotlin Multiplatform client for Mistral AI.

Blowhard is in early development. The current foundation provides a shared Compose presentation
module for desktop, Android, and iOS, a dedicated desktop entry point, and a platform-neutral core
API for starting, observing, and cancelling generation runs.

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
- Gitleaks 8.30 or newer for the local security gate

The toolchain is pinned in the Gradle version catalog and documented in
[`ADR 0001`](docs/decisions/0001-toolchain-and-project-shape.md).

The shared client contract and enforced module direction are documented in
[`ADR 0002`](docs/decisions/0002-core-api-and-dependency-boundaries.md).

The local quality and security gate is documented in
[`ADR 0003`](docs/decisions/0003-quality-gates.md).

## Run the desktop application

```shell
./gradlew :apps:desktop:run
```

## Verify the foundation

```shell
./gradlew qualityCheck
```

The aggregate gate checks formatting, static analysis, tests, coverage thresholds, module
boundaries, API compatibility, KDoc generation, and secrets in both Git history and the working
tree. Apply safe formatting changes with `./gradlew spotlessApply`.

Generated API documentation is written to `build/dokka/html`, and the XML coverage report is
written to `build/reports/kover/report.xml`.

On macOS, also verify the shared iOS target:

```shell
./gradlew :shared-ui:compileKotlinIosSimulatorArm64
```

Native desktop installers are built on their corresponding operating systems; cross-packaging is
not supported.

The application currently displays only the runnable project foundation. AI functionality and
release artifacts are not available yet.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the review and verification workflow.

Blowhard is an independent project and is not affiliated with Mistral AI.
