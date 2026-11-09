# ADR 0005: Verify native desktop packages on their target hosts

- Status: Accepted
- Date: 2026-11-09

## Context

A JVM build does not demonstrate that a desktop application can be installed and started by a
user who does not have a JDK. Native installers also introduce platform identity, architecture,
runtime composition, signing, and accidental-content risks that ordinary unit tests do not cover.

Compose Multiplatform delegates native packaging to `jpackage` and does not support
cross-packaging. Package verification therefore has to execute on each target operating system.

## Decision

- Use installer schema version `1.0.0` for an Apple-silicon `.dmg`, x64 `.msi`, and amd64 `.deb`.
  A positive major component is required by the JDK's macOS packaging tool. CI packages remain
  verification evidence rather than a product release.
- Keep `io.github.artemidius.blowhard` as the macOS bundle identifier and a constant Windows
  upgrade UUID. Use the lowercase `blowhard` Debian package name.
- Bundle only the declared JDK modules required by the current Compose application.
- Extend the existing required desktop CI checks so each host builds, inspects, installs, and
  launches its native package.
- Launch the real installed application with `BLOWHARD_PACKAGING_SMOKE_TEST=1`. This mode opens
  the Compose window and exits after the first frame, making installation checks deterministic.
- Inspect the packaged tree for private build paths, internal planning names, credentials, key
  material, broken or external symlinks, and the presence of a bundled Java runtime.
- Retain CI packages for seven days as verification evidence. Do not treat them as releases.

## Security and release status

CI packaging uses no signing credentials. The macOS app receives an ad hoc signature from the
packaging tool, but is neither Developer ID signed nor notarized. The Windows package is not
publisher-signed. CI asserts these states, and the limitations are disclosed rather than hidden
behind a release claim. Signing and long-lived publication require a separate, credential-isolated
release design.

Only repository-controlled application outputs are package inputs. Local environment files,
workspace metadata, internal plans, and signing files are not packaging resources.

## Consequences

The required desktop checks now prove that the application is self-contained and launchable from
all three native package formats. The supported package architectures are explicit instead of
being inferred from generic operating-system names.

Packaging tasks run without Gradle's configuration cache because the current Compose packaging
tasks are not configuration-cache compatible. CI time increases, and macOS verification remains
subject to hosted Apple-silicon runner capacity.
