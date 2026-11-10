# ADR 0023: Bounded content-free run inspection

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The run lifecycle already classifies failures and owns exact routing, configuration, and timing,
but clients could observe only content-bearing run snapshots. A portfolio-grade inspector needs
useful operational evidence without becoming a second path for prompts, model output, credentials,
or provider bodies. Recording every streamed text delta would also make diagnostic cost scale with
response length.

Mistral streaming usage is optional and must be explicitly requested. Treating absence as zero
would turn unknown telemetry into false evidence.

## Decision

Add a provider-neutral `RunInspector` read contract and immutable `RunInspection` model. Allowlist
opaque identities, target/model, generation parameters, lifecycle timestamps, terminal status,
safe failure category, retry guidance, and optional token counts. Do not include prompt, response,
credential, header, provider body, or failure-reference fields.

Keep one bounded inspection aggregate beside each process-owned run. Record accepted, started, and
terminal transitions; do not record text deltas. Cap public timelines at 32 chronological events so
future tool and document stages can add typed events without introducing unbounded growth.

Add provider-neutral `TokenUsage` with individually optional non-negative counts. Request Mistral
streaming usage through `stream_options.include_usage`, map only documented count fields, merge
compatible frames, and reject conflicting counts as an invalid successful stream. Preserve absent
usage as `null`.

Make inspection passive: lookup never invokes a provider or mutates run state. Keep this first
increment process-local. Persistence, shared presentation, and user-selected export remain separate
reviewable changes.

## Alternatives considered

### Reuse content-bearing run snapshots as diagnostics

Rejected because prompts and generated output would become easy to log or export accidentally,
and snapshots lack a bounded lifecycle timeline.

### Redact arbitrary diagnostic maps at export time

Rejected because a denylist cannot reliably identify future credentials or sensitive content.
The core model should make unsafe fields unrepresentable.

### Record every streamed delta as an event

Rejected because event count and observer work would scale with provider chunking rather than with
meaningful operational transitions.

### Convert missing usage to zero

Rejected because zero tokens is a factual value. Missing provider telemetry remains unknown.

## Consequences

- Headless clients can inspect safe lifecycle evidence without accessing conversation content.
- Long streams have constant inspection-event cost.
- Demo versus live model routing and retry policy are explicit and testable.
- Optional token usage is requested and retained without inventing absent counts.
- The shared inspector UI is delivered as a separate consumer of this contract; restored history
  and sanitized file export still require later increments.
