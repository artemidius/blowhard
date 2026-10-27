# ADR 0031: bounded, allowlisted tool orchestration

## Status

Accepted.

## Context

Meeting-note generation benefits from retrieving small pieces of an attached document. Treating provider tool calls as
trusted application commands would allow model output to control scope, schemas, concurrency, and termination.

## Decision

Define provider-neutral tool contracts in the domain layer and a fixed registry in the application layer. The model
may select only registered names and provide arguments accepted by a strict JSON schema. One application-owned state
machine enforces step, call, per-step, duration, and output ceilings; rejects duplicate call identities; executes
validated calls sequentially; records a content-safe trace; and preserves cancellation.

The first tools are read-only `search_text` and `read_page`. Both accept opaque document identities and resolve only
against the documents attached to the current run. Returned excerpts are bounded and explicitly treated as untrusted
source material.

The Mistral adapter maps these contracts to the provider's function-calling representation. It does not own policy,
tool registration, or local authorization.

## Consequences

- A model cannot add tools, expand scope, choose arbitrary paths or hosts, or keep a loop alive indefinitely.
- Deterministic application tests cover orchestration independently of provider DTO mapping.
- Tool capability remains intentionally narrow; new tools require code, schema, tests, and review.
- Mutations do not share the ordinary read registry and require a separate approval boundary.
