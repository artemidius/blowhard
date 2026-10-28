# ADR 0003: Local quality and security gates

- Status: accepted
- Date: 2026-11-09

## Context

The project needs repeatable evidence that changes preserve code style, architectural boundaries,
documented contracts, test coverage, and basic supply-chain hygiene. Individual developer or IDE
settings are not sufficient because they are easy to omit and difficult to review.

## Decision

Expose one Gradle entry point, `qualityCheck`, that runs the project checks required before review:

- Spotless with ktlint for reproducible source and build-script formatting.
- Detekt with strict KDoc and focused size limits for Kotlin static analysis.
- desktop-hosted common tests and Kover verification for executable core rules.
- Kotlin Gradle plugin ABI validation for public core and adapter contracts. The iOS application
  boundary is validated by the Apple CI job because its dependency graph includes native Apple
  interop libraries that cannot be resolved reliably on Linux.
- Dokka generation for aggregated core and adapter API documentation.
- the existing project-dependency allowlist for module direction.
- Gitleaks against Git history and the working tree, with narrow generated-state and ignored local
  credential-file exclusions.

Line coverage for the currently executable application and prompt-validation rules must remain at
or above 85 percent, with branch coverage at or above 75 percent. Coverage is a regression signal,
not a substitute for behavior-focused assertions. DTOs and ports are excluded from the threshold
because executing constructors does not demonstrate meaningful behavior.

Gradle dependency verification records SHA-256 checksums for resolved build inputs. Dependabot is
configured to propose grouped toolchain and quality-tool updates for review.

Detekt 2.0 is pinned to a named pre-release because its Kotlin and Gradle baseline matches this
project's current toolchain. The pin must be replaced by a stable 2.x release after compatibility is
verified; automatic upgrades remain review-only.

## Consequences

- Contributors need JDK 21, Android SDK 36, and Gitleaks for the complete local gate.
- Intentional public API changes require review of and an explicit update to committed ABI dumps.
- Tool upgrades may change rules or checksums and therefore require a dedicated, explainable diff.
- Operating-system CI and installer production remain separate concerns and are introduced in the
  next delivery increment.
