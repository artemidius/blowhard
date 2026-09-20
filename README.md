# Blowhard

[![CI](https://github.com/artemidius/blowhard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/artemidius/blowhard/actions/workflows/ci.yml)

A desktop-first Kotlin Multiplatform client for Mistral AI.

Blowhard is in early development. The current application demonstrates a shared Compose
conversation workflow on desktop, backed by a platform-neutral run lifecycle and a deterministic
offline generation adapter. The shared presentation module also compiles for Android and iOS.

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

The cross-platform CI matrix and security model are documented in
[`ADR 0004`](docs/decisions/0004-continuous-integration.md).

Native package identity and verification are documented in
[`ADR 0005`](docs/decisions/0005-native-desktop-packaging.md).

Run ownership, concurrency, and deterministic fixture behavior are documented in
[`ADR 0006`](docs/decisions/0006-run-lifecycle-and-fixtures.md) and the
[run lifecycle guide](docs/architecture/run-lifecycle.md).

The unidirectional presentation model, safe Markdown subset, and desktop composition root are
documented in [`ADR 0007`](docs/decisions/0007-shared-conversation-presentation.md) and the
[conversation presentation guide](docs/architecture/conversation-presentation.md).

Native menus, clipboard and window-state boundaries, application directories, accessibility, and
the verified desktop support matrix are documented in
[`ADR 0008`](docs/decisions/0008-desktop-platform-integration.md) and the
[desktop platform support guide](docs/architecture/desktop-platform-support.md).

The first live-provider boundary discovers Mistral models through a provider-neutral catalog and a
hardened, fixed-host Ktor adapter. Its security and mapping decisions are documented in
[`ADR 0009`](docs/decisions/0009-mistral-model-catalog-boundary.md) and the
[Mistral provider boundary guide](docs/architecture/mistral-provider-boundary.md).

The same adapter module implements bounded, cancellation-aware Mistral chat streaming without
automatic POST replay. Its framing, terminal-state, and retry decisions are documented in
[`ADR 0010`](docs/decisions/0010-mistral-streaming-chat.md) and the
[streaming chat guide](docs/architecture/mistral-streaming-chat.md).

Secure credential lifecycle is isolated behind a provider-neutral store. The macOS implementation
uses native Keychain generic-password items and other desktop hosts currently fail closed. The
boundary and its limitations are documented in
[`ADR 0011`](docs/decisions/0011-macos-keychain-credentials.md) and the
[credential storage guide](docs/architecture/credential-storage.md).

Live-provider configuration is coordinated through a shared, secret-free settings contract. It
requires a verified compatible model and explicit external-processing acknowledgement before live
mode can be selected. The policy is documented in
[`ADR 0012`](docs/decisions/0012-explicit-live-provider-settings.md) and the
[live-provider settings guide](docs/architecture/live-provider-settings.md).

## Run the desktop application

```shell
./gradlew :apps:desktop:run
```

The runnable application is an explicitly labeled offline demo. Choose a bundled success, failure,
rate-limit, or interrupted-stream scenario, enter any non-blank prompt, then use **Send** or
**Stop** to exercise the real shared lifecycle. No API key or network request is required.

Use `Command+Enter` on macOS or `Ctrl+Enter` on Windows and Linux to send from the composer. Native
menus expose New conversation, Send, Stop, Copy latest response, and Exit with platform-appropriate
shortcuts. Every non-empty message also has a visible copy action.

## Verify the foundation

```shell
./gradlew qualityCheck
```

The aggregate gate checks formatting, static analysis, tests, coverage thresholds, module
boundaries, API compatibility, KDoc generation, and secrets in both Git history and the working
tree. Apply safe formatting changes with `./gradlew spotlessApply`.

Generated API documentation is written to `build/dokka/html`, and the XML coverage report is
written to `build/reports/kover/report.xml`.

GitHub Actions builds and tests the desktop JVM application on pinned Linux, Windows, and macOS
runners. Separate jobs compile Android shared code with host tests and compile Apple device and
simulator targets with simulator tests. The workflow uses read-only permissions and no provider
credentials.

On macOS, also verify the shared iOS target:

```shell
./gradlew :shared-ui:compileKotlinIosSimulatorArm64
```

## Native desktop packages

Native installers are built only on their corresponding operating systems because Compose does
not support cross-packaging:

| Host | Package | Architecture | Gradle task |
| --- | --- | --- | --- |
| macOS | `.dmg` | Apple silicon (`arm64`) | `:apps:desktop:packageDmg` |
| Windows | `.msi` | `x64` | `:apps:desktop:packageMsi` |
| Linux | `.deb` | `amd64` | `:apps:desktop:packageDeb` |

Run packaging tasks with `--no-configuration-cache`; the Compose packaging plugin does not yet
support Gradle's configuration cache for these tasks. Each package contains a minimized Java
runtime and does not require a separately installed JDK.

CI builds each native format, inspects its contents, installs it, and launches the installed
Compose application in a deterministic smoke mode. The package archives are retained with the CI
run for seven days; they are not published releases.

The current packages have no trusted publisher signature. `jpackage` applies an ad hoc signature
to the macOS app, but it is not Developer ID signed or notarized. The Windows package is not
publisher-signed. These packages are therefore intended only for build verification; trusted
signing and release distribution are intentionally deferred.

The runnable application still uses synthetic fixture output rather than live Mistral generation.
The repository includes isolated, tested model-catalog and streaming-chat transport boundaries, but
the macOS credential store and provider boundaries are not yet exposed in the UI. Public release
artifacts are not available yet.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the review and verification workflow.

Blowhard is an independent project and is not affiliated with Mistral AI.
