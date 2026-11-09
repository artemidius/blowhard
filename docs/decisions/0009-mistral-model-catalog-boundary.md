# ADR 0009: Introduce Mistral through a model-catalog boundary

- Status: accepted
- Date: 2026-11-09

## Context

The application needs provider capabilities before it can safely enable model-specific workflows.
Mistral's model catalog exposes availability, context-window, alias, and capability information, but
its HTTP and JSON types must not become the shared application's public contract. Credentials and
provider error content also require a deliberately narrow security boundary.

## Decision

- Define `ModelCatalogPort` and its immutable result types in `core:domain`. The contract represents
  only the information the application currently understands and uses explicit `UNKNOWN` states
  when the provider omits availability or capability values.
- Implement the port in `adapters:mistral` with Ktor common code and platform engines: CIO on
  desktop, OkHttp on Android, and Darwin on Apple platforms.
- Request only `https://api.mistral.ai/v1/models`. Do not accept caller-controlled endpoints, follow
  redirects, or attach authorization outside that fixed request.
- Obtain the API key through a suspendable credential port immediately before the request. Reject
  blank, oversized, whitespace-containing, or control-character-containing values before network
  access. Credential storage is outside this increment.
- Parse successful responses strictly enough to reject invalid identifiers, duplicate identifiers,
  non-positive context windows, and malformed JSON. Ignore additive unknown JSON fields so provider
  evolution does not break a valid catalog.
- Translate HTTP and transport failures into provider-neutral categories. Never expose or retain a
  provider error body. Honor only bounded numeric `Retry-After` values.
- Keep all adapter contract tests offline with Ktor's mock engine. Tests assert request shape,
  conservative mapping, cancellation, redaction, status handling, and rejection of cross-host
  redirects.

The implementation follows Mistral's documented
[`GET /v1/models`](https://docs.mistral.ai/api/endpoint/models) contract and its published
[API error categories](https://docs.mistral.ai/resources/error-glossary/).

## Consequences

Shared code can reason about model support without depending on Mistral DTOs, Ktor, or a particular
platform engine. A missing field cannot silently enable a feature, and an HTTP redirect cannot move
the bearer credential to another host.

This increment does not make live generation available. It also does not add credential
persistence, automatic retries, regional endpoint selection, model selection UI, or live-provider
tests. Those capabilities require separate decisions and can build on the same core-owned boundary.
