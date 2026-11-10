# ADR 0025: Deterministic native release evidence

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The desktop CI proves that native installers can be built, inspected, installed, and launched, but
the uploaded packages previously had no portable metadata binding them to a public product version,
source revision, target architecture, or checksum. CI run names and package filenames are not a
sufficient release contract.

Native installer version rules also differ from the intended public `0.1.0` release. In particular,
the JDK macOS packager requires a positive major installer component. Hiding that distinction would
make package identity misleading.

## Decision

Centralize `blowhardReleaseVersion` and `blowhardInstallerVersion` in `gradle.properties`. All
Gradle projects inherit public version `0.1.0`; native package metadata uses schema version `1.0.0`.
Document the latter as a packaging constraint, not a product maturity claim.

After each target-host package passes installation and launch verification, generate a deterministic
JSON manifest and `SHA256SUMS`. Bind the package to the exact source revision, public and installer
versions, supported operating-system/architecture pair, observed signing category, filename, byte
size, and SHA-256 digest. Reject unknown targets, multiple candidate files, unstable versions,
invalid revisions, stale manifests, and modified checksum files.

Keep timestamps, absolute paths, CI identities, and service URLs out of the evidence. Upload the
manifest and checksum with the existing short-lived CI package. Do not publish a GitHub Release in
this increment.

## Alternatives considered

### Rely on the CI artifact name and run page

Rejected because those values are service metadata rather than a portable artifact contract, and
they do not verify downloaded bytes.

### Use one version for product and native installers

Rejected because `0.1.0` is not accepted by the current macOS package tool. Pretending the public
release is `1.0.0` would be inaccurate.

### Include a generation timestamp

Rejected because it makes identical inputs produce different evidence without strengthening the
binding to source or artifact bytes.

### Treat SHA-256 as publisher authentication

Rejected because a checksum provides integrity only when obtained through a trusted channel. Code
signing and protected publication remain separate release increments.

## Consequences

- Every CI installer is accompanied by machine-verifiable, path-free metadata.
- Release automation has one reviewed source for public and native package versions.
- Candidate tampering and stale evidence fail before upload.
- Package filenames continue to contain installer schema `1.0.0`; the manifest carries public
  release `0.1.0` explicitly.
- Signing, dependency-license inventory, installation documentation, and guarded publication still
  require later P12 increments.
