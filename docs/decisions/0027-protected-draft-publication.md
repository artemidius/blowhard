# ADR 0027: Build a protected draft release from immutable source

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Short-lived CI artifacts prove package construction but are not a release channel. Publication adds
write authority, a durable tag, public-facing notes, and a larger supply-chain impact. Giving every
build job write permission or creating a public release automatically would make review and failure
recovery unnecessarily risky.

The project is maintained by one person today, packages are not trusted-publisher signed, and the
first milestone must remain honest about its preview status.

## Decision

License Blowhard under MIT and embed the repository license in native package metadata. Require the
exact `v<blowhardReleaseVersion>` tag, a `main` workflow dispatch, reviewed versioned notes, and an
unused tag/release destination before building.

Rebuild the selected commit on Linux, Windows, and macOS. On every host, audit runtime licenses,
inspect and install the package, execute the deterministic smoke path, generate source-bound release
evidence, and stage uniquely named assets. Rerun the complete quality gate separately and stage the
MIT license plus generated KDoc.

Grant `contents: write` only to a final job behind the `release` GitHub environment. Download the
cross-runner artifacts, re-verify the closed target matrix and all hashes/metadata, then use the
ephemeral `GITHUB_TOKEN` to create a draft prerelease. Never overwrite existing tags, releases, or
assets. Publishing the draft remains an explicit maintainer action.

## Alternatives considered

### Publish directly after ordinary CI

Rejected because a pull-request check is not a deliberate release authorization and its jobs do not
need repository write access.

### Create a fully public release automatically

Rejected because unsigned preview packages and release notes need a final human review.

### Use a personal access token

Rejected because the repository-scoped `GITHUB_TOKEN` is short-lived and sufficient for draft
creation.

### Repackage artifacts on the publication runner

Rejected because native packages must be built, installed, and verified on their target operating
systems. The publication runner only validates and transfers already checked assets.

## Consequences

- Release write authority is isolated behind an environment approval and one job.
- Every durable candidate is traceable to one `main` commit and a closed three-host evidence set.
- Drafts cannot silently replace earlier release state.
- A single maintainer can approve their own environment deployment; adding another maintainer should
  enable prevention of self-review.
- Environment protection cannot be bypassed by repository administrators.
- Trusted signing, notarization, and post-publication immutable-release settings remain future
  hardening work.
