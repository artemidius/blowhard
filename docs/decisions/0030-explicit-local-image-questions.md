# ADR 0030: Gate local image questions by explicit capability and consent

- **Status:** Accepted
- **Date:** 2026-11-11

## Context

An imported image is private local data. Selecting a model, importing a file, or asking a text
question is not independently sufficient consent to upload that image. Vision support also varies
by model, and guessing from a model name would make the boundary unreliable as the provider catalog
changes.

The provider accepts image input inside chat messages, including base64 data URIs. The application
therefore needs one auditable path from a stored image to a single accepted attempt without exposing
native paths, accepting remote URLs, or retaining encoded content in presentation state.

## Decision

Expose a provider-neutral run attachment containing only an opaque document identity. The shared
composer lists structurally inspected PNG and JPEG imports only when the verified provider catalog
explicitly reports vision support for the selected model. A user must choose one image for the next
prompt; changing text or opening a document does not attach anything.

At attempt acceptance, the application snapshots the selected model capability before reading the
document. It resolves only the selected app-owned record, verifies that it is still a supported
image, copies bounded bytes into an immutable generation request, and rejects missing, unsupported,
or non-vision combinations without calling the provider.

The Mistral adapter emits ordered chat content: the text part first and one `image_url` part second.
The URL is a locally constructed PNG or JPEG base64 data URI. Arbitrary local paths and remote URLs
are not accepted. Existing text-only requests retain their original string representation.

Attachment selection is consumed only by an accepted start. A rejected start restores the explicit
selection so the user can correct settings or remove it. Regeneration never silently reuses a prior
image; attaching it again requires a new explicit selection.

## Alternatives considered

### Infer vision support from model names

Rejected because naming is not a stable capability contract and a false positive could disclose
content to an incompatible model.

### Send every imported image as conversation context

Rejected because import is a local ownership action, not consent to external processing.

### Store data URIs in presentation or conversation history

Rejected because this would duplicate sensitive bytes, enlarge observable state, and complicate
retention and deletion claims.

### Reuse an attachment during regeneration

Rejected because each provider submission is a separate disclosure and potentially billable
attempt.

## Consequences

- One explicit image and one prompt form one immutable attempt.
- Provider capability is fail-closed: absent vision metadata disables attachment.
- UI and diagnostics retain only the display name, document identity, and attempt provenance.
- The source stays in app-owned storage until request construction and remains governed by local
  document deletion.
- Mistral may process or retain submitted content under its own service terms; local deletion cannot
  recall a completed chat request.
- PDF conversation attachment, multiple images, remote image URLs, and automatic attachment reuse
  are intentionally unsupported.
