# ADR 0016: Snapshot conservative generation controls per attempt

- Status: accepted
- Date: 2026-11-10

## Context

Generation settings can change while an accepted attempt waits for an execution permit. Reading
mutable settings inside a provider adapter would make the request depend on timing and would make a
regenerated answer difficult to explain. Provider wire options also should not leak into the shared
run contract without a portable meaning.

Mistral's current
[chat-completion reference](https://docs.mistral.ai/api) documents
`max_tokens`, `temperature`, and `top_p`. It recommends changing temperature or `top_p`, not both,
and recommends a temperature from 0.0 through 0.7. Model defaults can vary.

## Decision

- Add provider-neutral `GenerationParameters` containing an optional maximum output-token count
  and optional temperature. Null means that the request omits the field and uses the model default.
- Support temperature only in the documented recommended range from 0.0 through 0.7. Do not expose
  `top_p` at the same time, avoiding two controls that Mistral advises against changing together.
- Validate the shared values before a run can reach an adapter. The settings UI offers a bounded set
  of useful presets rather than an arbitrary numeric text field.
- Snapshot the complete parameter value alongside the target when a run is accepted. Queueing,
  later settings changes, and regeneration cannot mutate that run's request.
- Map the snapshot to Mistral `max_tokens` and `temperature`. Omit both keys when model defaults are
  selected. Keep run and conversation identifiers local.
- Pass the same typed snapshot to the deterministic fixture. The fixture remains deterministic and
  does not pretend to emulate sampling behavior.

## Consequences

The shared API can describe common generation intent without depending on Mistral DTOs. Every
provider request is reproducible from its accepted in-memory configuration, and model defaults stay
distinguishable from explicit zero-like values.

The application does not yet persist or display historical parameter snapshots. Durable attempt
configuration belongs with conversation storage, and additional controls require their own
provider-capability and interaction design.
