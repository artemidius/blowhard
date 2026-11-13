# ADR 0070: Android cold-start evidence

## Status

Accepted.

## Context

An installable APK and a passing UI test prove compatibility but do not make startup regressions
visible. Startup evidence must use the real application process without adding production telemetry,
capturing user content, or depending on provider credentials.

## Decision

After Android device tests, force-stop the debug application and cold-launch its exported activity
with Android's `am start -W` contract. Require a successful status, a live application process, and a
generous 10-second CI ceiling. Persist only package identity, component identity, launch state, the
reported timing fields, and the declared ceiling as versioned JSON.

Keep parsing and budget policy in a standard-library Python tool with deterministic unit tests. The
device run remains the authoritative integration check; the ceiling is a regression tripwire rather
than a product performance claim.

## Consequences

- Every Android pull request produces inspectable cold-launch timing evidence.
- Launch failures and severe startup regressions fail before merge.
- No analytics SDK, runtime permission, credential, prompt, or response is introduced.
- iOS CI continues to build, install, launch, and verify process survival; `simctl launch` does not
  expose an equivalent app-ready timing contract, so the repository does not fabricate one.
