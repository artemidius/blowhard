# ADR 0028: Bound and inspect local document imports

- **Status:** Accepted
- **Date:** 2026-11-11

## Context

Future AI workflows need durable user-selected context, but a file picker is not a trust boundary.
Extensions and declared media types can be wrong, files can change while being read, and unbounded
documents can exhaust memory before any provider request exists. Passing native paths into shared
presentation would also couple every client to desktop filesystem details and make accidental path
disclosure easier.

The first increment needs a useful local lifecycle without implying extraction, embeddings,
retrieval, or automatic provider upload.

## Decision

Expose path-free import, list, lookup, and deletion results through `core:api`. Keep content
inspection and storage as separate `core:domain` ports, then coordinate them in
`core:application`. The shared presenter receives only verified metadata and typed, content-safe
outcomes.

Read one explicitly selected desktop file through a bounded source adapter. Do not follow symbolic
links, preserve coroutine cancellation, and copy at most 20 MiB. Treat the native extension filter
as a user-interface hint only. Detect PDF, PNG, and JPEG from bytes in a portable inspector. Verify
structural boundaries and checksums where available, then enforce 100 PDF pages or 40 megapixels.
Reject encrypted PDFs and PDF object streams rather than claiming support for structures the
conservative parser does not interpret.

Store verified metadata and copied bytes atomically in the app-owned SQLDelight database. Generate
opaque identities in the desktop composition root. Keep those identities stable across restart and
delete the metadata and bytes together only after explicit confirmation. A storage startup failure
must leave the application usable while document operations fail closed.

## Alternatives considered

### Trust extensions or picker media types

Rejected because both are attacker-controlled metadata and do not establish the selected bytes'
format or resource cost.

### Parse documents in shared UI

Rejected because presentation should coordinate user intent, not own binary parsing, persistence,
or platform paths.

### Add a full PDF/image processing dependency immediately

Rejected for this stage because the product does not yet extract or render content. The smaller
inspector has an auditable, deliberately narrow acceptance surface and compiles unchanged for the
current KMP targets.

### Send a document to Mistral during import

Rejected because local ownership and external processing are separate user decisions. Import never
starts a provider request.

## Consequences

- Desktop can import, list, restart, and delete locally owned PDF, PNG, and JPEG documents.
- Shared UI remains mobile-ready and free of native paths or desktop picker types.
- Memory use and accepted document complexity have explicit ceilings.
- The original selected file is never modified or deleted.
- Local SQLite storage is not encrypted and deletion is not secure erasure.
- Some valid complex PDFs are conservatively rejected until a reviewed parser boundary replaces or
  extends the portable inspector.
- Content extraction, retrieval, attachments, and provider upload remain future, explicit stages.
