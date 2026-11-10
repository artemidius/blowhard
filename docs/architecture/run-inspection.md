# Run inspection

Blowhard exposes operational run metadata through the provider-neutral `RunInspector` contract.
The shared inspector screen consumes that contract directly while the collection remains
intentionally process-local. The diagnostic export generator reuses only this safe model.

```mermaid
flowchart LR
    Provider[Fixture or Mistral adapter] --> Events[Generation events]
    Events --> Runs[Application run lifecycle]
    Runs --> Ledger[Bounded inspection ledger]
    Client[Shared UI or headless client] --> Inspector[core:api RunInspector]
    Inspector --> Ledger
    Inspector --> Generator[Deterministic JSON generator]
    Generator --> Saver[Platform user-selected saver]
```

## Allowlisted metadata

Each `RunInspection` contains only:

- opaque run and conversation identities;
- demo or live routing and the selected live model identifier;
- the accepted maximum-output and temperature controls;
- accepted, provider-started, and terminal timestamps;
- succeeded, failed, or cancelled terminal status;
- safe failure category, retry advice, and a bounded retry delay;
- optional provider-reported input, output, and total token counts.

The model has no field for prompts, generated output, credential values, authorization headers,
provider response bodies, or failure references. This is omission by construction, not a regex
redaction pass. Adapter failure references may remain available to the run lifecycle where already
classified as safe, but the inspector deliberately excludes them.

## Bounds and semantics

`RunInspection.MAX_EVENTS` caps one inspection at 32 chronological events. The current text
generation lifecycle emits at most three: accepted, started, and finished. Text deltas update the
conversation snapshot but never create inspection events, so a long token stream does not grow the
timeline. Usage updates replace one optional aggregate rather than appending events.

`TokenUsage` requires at least one non-negative reported count. Each count is nullable, and the
entire aggregate is nullable. Missing provider data therefore remains unknown rather than becoming
an invented zero. Usage does not determine success; it is attached only when the adapter reports a
valid aggregate before completion.

Inspection is passive. Looking up a run cannot start, repeat, cancel, or contact a provider. An
unknown or locally deleted run returns `NotFound`. Terminal inspection is updated alongside the
same immutable terminal transition observed by run clients.

## Shared presentation

Assistant attempts expose an **Inspect run** action. The shared presenter owns lookup loading,
not-found, and generic-failure states and cancels a superseded request. It never receives prompt or
response text because those fields do not exist in `RunInspection`.

The screen separates execution identity, generation controls, optional usage, terminal outcome,
and the chronological lifecycle. Missing usage is shown as `Unknown`; a run without a terminal
event is shown as `In progress`. Refresh repeats only the passive lookup. Restored attempts return a
clear process-local-unavailable state instead of reconstructing incomplete telemetry from message
history.

## Explicit diagnostic export

**Export JSON** performs a fresh passive lookup and creates a schema-versioned report from
`RunInspection` alone. The generator cannot access prompts, response snapshots, credentials,
provider bodies, provider references, authorization headers, native errors, or filesystem paths.
It uses a fixed filename and rejects reports above the documented 65,536-character boundary.

The shared presenter depends on `RunDiagnosticReportSaver`, not on a filesystem API. Desktop
implements that capability with a native save dialog and an atomic UTF-8 replacement in the chosen
directory. Cancellation writes nothing. Successful, cancelled, and unavailable outcomes remain
typed, while the shared UI exposes no destination path or native exception detail.

The checked-in [synthetic report](../examples/sanitized-run-diagnostic.json) documents schema
version 1 without publishing a real run identity. See
[`ADR 0024`](../decisions/0024-explicit-sanitized-diagnostic-export.md) for the trust boundary.

## Current limitations

- Inspections exist only for runs owned by the current process and are not yet restored from
  SQLite.
- Provider-reported counts are not independently recalculated and are not billing evidence.
- Tool, document, and approval events will extend the bounded model in later milestones.

These limitations keep restored telemetry and future lifecycle expansion separate from the safe
inspection and explicit export boundaries.
