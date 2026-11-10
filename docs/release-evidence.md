# Release evidence

Blowhard treats a native installer and the evidence describing it as one candidate set. Every
desktop CI host builds one installer, installs and launches it, then emits a deterministic manifest
and checksum file beside the package artifact.

## Version identity

The public foundation release is `0.1.0`. Gradle reads that value from
`blowhardReleaseVersion` in `gradle.properties`, and all projects inherit it.

Native package tools use the separate installer schema version `1.0.0`. The JDK's macOS packaging
tool requires a positive major component, so this value is intentionally distinct from the public
release version. It controls native package metadata and filenames; it does not claim that the
product has reached version 1.0.

## Candidate manifest

`release_evidence.py` records only reproducible inputs and derived artifact properties:

- schema, project, public release, and native installer versions;
- the exact 40-character source revision;
- operating system, architecture, package format, and signing status;
- installer filename, byte size, and SHA-256 digest.

It does not include timestamps, runner paths, actor names, credentials, or CI-internal URLs. The
manifest and `SHA256SUMS` are re-read and checked against the built installer before CI uploads the
candidate set.

Supported candidate targets are deliberately closed:

| Operating system | Architecture | Format | Recorded signing status |
| --- | --- | --- | --- |
| macOS | `arm64` | `.dmg` | ad hoc |
| Windows | `x64` | `.msi` | unsigned |
| Linux | `amd64` | `.deb` | unsigned |

The checksum detects changed or incomplete downloads. It does not establish publisher identity or
replace code signing. Until signing is implemented, candidates remain verification artifacts and
the signing limitation must travel with any installation instructions.

## Local verification

After building the package for the current host, create and verify its evidence with the source
revision under test:

```shell
python3 tools/packaging/release_evidence.py create . \
  --platform macos \
  --architecture arm64 \
  --source-revision "$(git rev-parse HEAD)"
python3 tools/packaging/release_evidence.py verify . \
  apps/desktop/build/release-evidence/macos-arm64/manifest.json
```

Linux uses `linux`/`amd64`; Windows uses `windows`/`x64`. Evidence is generated under the ignored
`apps/desktop/build/release-evidence` directory and is uploaded by CI with the corresponding native
package.
