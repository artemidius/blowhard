# ADR 0061: Mobile document processing composition

## Status

Accepted.

## Context

Android and iOS already share bounded document import, portable inspection, durable metadata, and
document-aware conversation context. Their OCR service still used a fail-closed placeholder, so an
explicit processing request could not reach Mistral even when the user's credential was valid.

## Decision

Create one `MistralDocumentOcrPort` inside each mobile provider composition from the same credential
provider used by model discovery and text generation. Inject that port into the platform document
session; the document application service remains provider-neutral and continues to validate every
remote response before publishing an extraction.

The provider composition owns and closes the OCR HTTP client. Document sessions own only local
stores and close them before provider resources during session replacement or local-data erasure.
Native persistence tests inject deterministic recording ports to prove the boundary is active without
making CI depend on a live account.

## Consequences

- Explicit OCR works on Android and iOS with the configured Mistral credential.
- OCR responses remain subject to shared page, size, ordering, and Markdown validation.
- Document-aware chat consumes only durable validated extractions, not raw provider output.
- Mobile tests remain deterministic and do not expose credentials or send external requests.
