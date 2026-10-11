# Desktop dependency and license inventory

Every desktop candidate carries an inventory generated from the resolved `runtimeClasspath` on the
host that built it. This keeps platform-specific runtime components visible while excluding build
plugins, tests, fixtures used only by tests, and Blowhard's own project modules.

## Policy gate

The report normalizes declared dependency licenses to SPDX identifiers. The reviewed allowlist is
stored in [`config/allowed-runtime-licenses.json`](../config/allowed-runtime-licenses.json) and
currently accepts dependencies available under Apache-2.0 or MIT terms. A dependency may declare
more than one option: JNA, for example, remains visibly dual-licensed while satisfying the policy
through its Apache-2.0 option.

`checkLicense` fails when a resolved external dependency has no allowed declared license. The
independent inventory verifier also rejects:

- an empty or malformed dependency list;
- missing versions or identified licenses;
- internal Blowhard modules in the third-party report;
- duplicate or non-deterministically ordered coordinates;
- absence of representative Compose, Ktor, SQLDelight, JNA, or Kotlin runtime components.

This is an automated engineering control, not legal advice. New or changed dependencies still
require review. The inventory describes third-party software; Blowhard itself is distributed under
the [MIT License](../LICENSE).

## Generate locally

Resolve the current host's desktop runtime, enforce policy, and verify the report:

```shell
./gradlew --no-parallel --no-configuration-cache \
  :apps:desktop:checkLicense \
  :apps:desktop:verifyLicenseInventory
```

Generated evidence is written to `apps/desktop/build/reports/dependency-license`:

- `dependencies.json` is a deterministic machine-readable inventory;
- `THIRD_PARTY_LICENSES.md` groups dependencies by normalized license;
- extracted embedded license files remain beside the Markdown links;
- `license-check-results.json` records any policy failure.

The directory is ignored locally. CI regenerates it from the reviewed source and uploads it with
the corresponding native desktop evidence. Gradle dependency verification covers the POM metadata
used to discover license declarations.
