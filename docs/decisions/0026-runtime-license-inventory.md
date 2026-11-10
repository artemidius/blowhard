# ADR 0026: Gate resolved desktop runtime licenses

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The version catalog lists direct dependencies but does not describe the transitive, host-specific
runtime graph included in a desktop candidate. A hand-maintained license table would drift silently,
and a report that scans every Gradle configuration would mix release components with build plugins
and test-only tools.

Release evidence must expose third-party terms without suggesting that an automated metadata scan
is a legal conclusion. It also needs a fail-closed policy for missing or newly introduced licenses.

## Decision

Apply the maintained Gradle dependency-license-report plugin only to `:apps:desktop` and resolve its
`runtimeClasspath` on each native build host. Exclude Blowhard project modules and BOM coordinates,
normalize declared licenses to SPDX identifiers, and emit timestamp-free Markdown plus sorted JSON.

Keep a reviewed local allowlist for Apache-2.0 and MIT. Accept a multi-licensed dependency when at
least one declared option matches policy, while preserving every discovered option in the report.
Run the plugin's policy check and an independent standard-library Python verifier. The verifier
guards against empty output, unknown licenses, internal-module leakage, unstable ordering,
duplicates, and accidental loss of representative runtime components.

Regenerate the report on Linux, Windows, and macOS after the package build. Upload the complete
report directory with the short-lived candidate evidence; do not commit generated inventories.
Verify the additional POM inputs through Gradle's existing checksum metadata.

## Alternatives considered

### Document only direct catalog entries

Rejected because it omits transitive and host-selected components that are relevant to a packaged
application.

### Scan every project and Gradle configuration

Rejected because build tools and tests are not part of the desktop runtime distribution and would
make the release report misleading.

### Commit one generated inventory

Rejected because native runtime selection varies by host. CI evidence must be generated from the
same source and operating system as its installer.

### Treat the scan as legal approval

Rejected because upstream metadata can be incomplete or wrong. The gate is a review aid and release
control, not legal advice.

## Consequences

- Every native candidate exposes its resolved third-party runtime graph and declared licenses.
- New, unknown, or disallowed dependency licenses fail required CI.
- The report remains deterministic and avoids timestamps and local paths.
- POM checksum coverage grows because license discovery reads dependency metadata.
- Project licensing, publisher signing, and long-lived release publication remain separate P12
  decisions.
