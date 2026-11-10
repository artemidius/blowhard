# Protected release process

Blowhard uses a manually dispatched workflow to assemble a release candidate from one immutable
`main` commit. The workflow can create only a **draft prerelease**. Publishing that draft remains a
separate human action after inspecting its notes, target matrix, signing disclosure, and assets.

## Repository controls

- The workflow has read-only repository permission by default.
- Only the final draft-creation job receives `contents: write`.
- That job targets the protected GitHub environment named `release`.
- The environment accepts deployments only from `main` and requires approval from the repository
  maintainer. Self-review is permitted because this is currently a single-maintainer project.
- Administrators cannot bypass the environment protection rules.
- Builds and tests receive no publishing permission or release secrets.
- Concurrent runs for the same tag are serialized and never cancel one another.

The workflow uses the ephemeral `GITHUB_TOKEN`; no personal token or long-lived publishing
credential is stored. Trusted platform signing is not configured, so the draft and release notes
must continue to disclose the current ad hoc/unsigned status.

## Start a candidate

From **Actions → Release candidate → Run workflow**:

1. Select `main`.
2. Enter the exact tag implied by `blowhardReleaseVersion`; for the foundation milestone this is
   `v0.1.0`.
3. Review the validation, quality, Linux, Windows, and macOS jobs.
4. Approve the waiting `release` environment only after all build evidence is green.

Validation rejects a different branch, malformed or mismatched tag, existing tag/release, missing
MIT license, missing release notes, or invalid source revision.

## Evidence assembled from each host

Each native job builds, audits, installs, and launches its own package before staging:

- the `.dmg`, `.msi`, or `.deb` installer;
- a uniquely named source/package manifest;
- its SHA-256 checksum file;
- a deterministic archive containing the host's third-party dependency/license inventory.

The quality job reruns the complete repository gate and stages the MIT license plus public KDoc.
It also installs checksum-verified actionlint and validates every workflow definition.
After downloading all cross-runner artifacts, the final job independently re-verifies the exact
three-target matrix, source revision, versions, package sizes and hashes, checksum contents,
license policy, safe archive paths, KDoc entry point, project license, and absence of unexpected
files.

## Review and publish

Successful automation creates tag `v0.1.0` and a draft marked as a prerelease. Before publishing it
in GitHub Releases:

- compare the displayed tag commit with the workflow source revision;
- review `docs/releases/v0.1.0.md` and every disclosed limitation;
- confirm all expected assets are present and no duplicate or stale draft exists;
- download at least the reference macOS package and verify its checksum and installation guide;
- leave the release as a prerelease while packages lack trusted publisher signatures.

The workflow never overwrites a tag, release, or release asset. If draft creation fails partway,
inspect the partial draft and tag before deciding whether to remove them; do not blindly rerun with
the same version.
