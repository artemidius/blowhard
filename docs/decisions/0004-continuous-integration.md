# ADR 0004: Cross-platform continuous integration

- Status: accepted
- Date: 2026-11-09

## Context

Desktop support means macOS, Windows, and Linux. A successful build on one developer machine does
not show that the project remains portable, and compilation alone does not exercise the common core
contracts on every JVM host. Android and Apple shared targets also need visible verification before
platform applications are introduced.

Pull requests from forks must remain safe to execute without repository or provider credentials.
Third-party workflow code and mutable runner aliases add supply-chain and reproducibility risk.

## Decision

Run one credential-free GitHub Actions workflow for pull requests, `main` pushes, and manual runs:

- `Quality` runs the complete local gate on Ubuntu, including dependency verification, coverage,
  API compatibility, documentation generation, and Gitleaks history/working-tree scans.
- `Desktop / Linux`, `Desktop / Windows`, and `Desktop / macOS` build the JVM application and run
  portable and desktop tests on fixed runner images, builds a native package, and launch-checks it.
- `Android` compiles and lints the application, runs portable and host tests, then uses an emulator
  to run native integration/UI tests, reinstall, cold-launch, and record bounded startup evidence.
- `Apple shared` validates the iOS application ABI, compiles iOS device and ARM64 simulator code,
  runs native simulator tests, builds the Xcode application without signing, verifies data
  protection, and installs and launches it in an iPhone simulator.

Use `ubuntu-24.04`, `windows-2025`, and `macos-15` instead of mutable `*-latest` labels. Set JDK 21
explicitly. Pin every action to a full commit SHA with the release tag retained in a comment for
review. Install a fixed Gitleaks archive only after its SHA-256 digest is verified.

Workflow permissions are read-only, checkout credentials are not persisted, and the workflow uses
`pull_request` rather than `pull_request_target`. Pull-request Gradle caches are read-only. Jobs have
explicit timeouts and concurrent superseded runs are cancelled. Sanitized build/test evidence is
retained for seven days; no environment or provider credentials are uploaded.

## Consequences

- Stable job names can be selected as required branch-protection checks after the workflow exists on
  the default branch.
- A runner-image or toolchain incompatibility fails visibly; it is not converted into a skipped
  success.
- Desktop package evidence and credential-free mobile build/install/launch evidence are produced on
  every change; distribution signing remains a separately disclosed concern.
- Action and runner-image updates require an explicit reviewed change rather than silently following
  a mutable major tag or `latest` image.
