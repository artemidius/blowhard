# ADR 0029: Make document OCR explicit, bounded, and durable

- **Status:** Accepted
- **Date:** 2026-11-11

## Context

Imported documents are local application data. OCR is different: it discloses the selected bytes
to an external processor and can return much more content than the source metadata suggests. A
presentation shortcut that uploads during import, exposes native paths, or accepts an unbounded
response would merge unrelated trust decisions and make resource use difficult to audit.

The feature must also survive restart without silently repeating a billable provider request.

## Decision

Define provider-neutral OCR requests, failures, extractions, pages, and lookup results in the core.
The application service reads only an already imported document, applies request and response
limits, invokes one OCR port, validates source identity and ordered page indices, then replaces the
stored extraction atomically. Cancellation is propagated and never converted into a retry.

The Mistral adapter sends a fixed-host `POST /v1/ocr` request only after the user chooses
**Extract with Mistral**. PDF bytes use the documented `document_url` data-URI representation;
PNG and JPEG bytes use `image_url`. The adapter fixes the OCR model, requests Markdown tables,
excludes generated images, bounds the encoded request and decoded response, verifies JSON content,
and maps provider or parsing failures to content-safe categories. Credentials, headers, source
bytes, provider bodies, and native paths never enter observable UI state or exception messages.

Persist the extraction header and ordered pages in the app-owned SQLDelight database. Reprocessing
replaces an extraction in one transaction. Deleting the local source deletes its extraction. The
shared workbench performs a passive lookup on open and displays persisted Markdown without making
a provider call; reprocessing remains a separate explicit action.

## Alternatives considered

### Upload automatically after import

Rejected because choosing a local file is not consent to external processing.

### Keep extraction only in presentation state

Rejected because restart would discard provenance and encourage unnecessary repeat requests.

### Give the provider an arbitrary remote URL

Rejected because Blowhard owns local bytes and must not introduce a server-side request-forgery or
ambient network-fetch boundary.

### Retry POST requests automatically

Rejected because an interrupted request may already have been accepted and could incur duplicate
work or cost.

## Consequences

- Import and OCR remain distinct, inspectable user actions.
- The shared API and workbench remain free of platform paths and Mistral transport types.
- Durable output keeps source identity, processor name, timestamp, page ordering, and Markdown.
- The original source and extraction stay in local SQLite until explicit document deletion.
- Mistral may retain or process submitted content under its own service terms; Blowhard cannot
  provide remote deletion for the data-URI request form.
- The source panel currently presents verified metadata and the selected page reference rather
  than rendering arbitrary PDF or image bytes in-process.
