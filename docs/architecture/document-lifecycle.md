# Local document lifecycle

Blowhard treats document selection, inspection, ownership, presentation, and provider processing
as distinct boundaries. Import remains entirely local. OCR begins only after a second, explicit
**Extract with Mistral** action.

```mermaid
flowchart LR
    User[Explicit user selection] --> Picker[Desktop path adapter]
    Picker --> Source[Path-free bounded source]
    Source --> Library[core:application document library]
    Library --> Inspector[Portable content inspector]
    Library --> Store[DocumentStore port]
    Inspector --> Policy[Size, page and pixel limits]
    Store --> SQLite[SQLDelight app-owned row]
    UI[Shared presenter and Compose UI] --> Library
    UI -->|explicit extraction| OCR[core:application OCR service]
    OCR --> Processor[Fixed-host Mistral OCR adapter]
    OCR --> ExtractionStore[Atomic extraction store]
    ExtractionStore --> SQLite
    Mobile[Future mobile picker] -. same source contract .-> Source
```

## Contract and dependency direction

`core:api` defines opaque document identities, immutable verified metadata, path-free import
sources, and typed lifecycle results. `core:domain` owns the inspection and storage ports.
`core:application` reads a selected source once, asks the inspector for verified characteristics,
applies policy, creates an identity and timestamp, then saves through the store.

`adapters:documents` implements the byte-level inspector in common Kotlin. `adapters:persistence`
maps metadata and copied bytes to one SQLDelight row and supplies desktop, Android, and Native
driver factories. `shared-ui` serializes picker, refresh, import, confirmation, and deletion actions
without knowing a native path or database type. The desktop composition root provides the AWT
picker and owns both the database session and presenter lifecycle.

The OCR boundary follows the same direction. `core:api` exposes immutable extraction pages and
typed processing results. `core:domain` defines the processor and extraction-store ports.
`core:application` owns validation and orchestration. The Mistral transport and SQLDelight schema
remain adapter details, while the shared workbench depends only on the core contracts.

## Import transaction

The native picker returns a desktop-only `DocumentImportSource`. Its public metadata is limited to
a filename and optional size. Reading happens on an I/O dispatcher, does not follow symbolic links,
checks cancellation between chunks, and stops as soon as content exceeds the configured maximum.
The resulting byte array is defensively copied at the API and persistence boundaries.

The portable inspector does not trust an extension:

- PNG requires the signature, one valid IHDR, bounded chunks, verified CRC values, image data, and
  a terminal IEND;
- JPEG requires a valid marker walk, supported frame dimensions, a scan header, bounded entropy
  data, and an end marker;
- PDF requires a supported header, terminal marker, and explicit page objects, while encrypted
  files and object streams fail closed.

Accepted content is limited to 20 MiB, 100 PDF pages, and 40 million image pixels. The SQLDelight
store saves identity, display metadata, verified format characteristics, and BLOB content in one
row. Constraints repeat important invariants at the storage boundary. A collision or failed write
does not publish a successful import.

## Presentation and restart behavior

One shared presenter processes actions sequentially so an import cannot race a refresh or confirmed
deletion. UI state contains immutable document metadata, a busy flag, one pending confirmation,
and typed notices. Unexpected picker, storage, or mapping failures become a generic unavailable
notice; native paths and exception messages do not enter observable state.

The desktop opens document storage in the same app-owned database used by conversation history but
through an independently owned store. A real composition test imports a synthetic PDF, closes the
session, reopens it, verifies the original identity and timestamp, deletes it, and confirms another
reopen is empty. Closing sessions and presenters is idempotent. If storage cannot open, the rest of
the application still starts and the document library returns only unavailable outcomes.

## Explicit OCR transaction

Opening a document performs only a local extraction lookup. Choosing **Extract with Mistral** reads
the already imported bytes, verifies the stored source identity, and submits one bounded request to
the fixed Mistral OCR endpoint. PDF, PNG, and JPEG data is encoded as a local data URI; Blowhard does
not ask the provider to fetch an arbitrary URL. The adapter requests page Markdown and tables while
excluding generated images.

The application accepts at most the configured page count and output size. Page indices must be
unique, contiguous, and ordered from zero, and every result remains tied to the imported document
identity. A successful result atomically replaces the previous extraction. Provider, transport,
content-type, schema, storage, and validation failures become typed notices. No POST is retried
automatically, and coroutine cancellation is preserved.

The workbench reads the durable extraction on restart and shows source metadata beside extracted
Markdown. Selecting a page is local presentation state. Reopening, refreshing, or changing pages
does not contact Mistral.

## Ownership, retention, and deletion

The selected original remains owned by the user and is never changed. Blowhard owns only its copied
database row. Imported documents remain until individually deleted; there is no automatic expiry or
background upload. Confirmation names the local document and states that the original is unaffected.
Metadata, copied content, and any stored extraction are removed together.

The database is not application-encrypted. It does not protect content from the same OS user,
malware, backups, filesystem snapshots, or a compromised operating system. SQLite deletion is not
secure erasure. Local deletion cannot erase content already processed under Mistral's service-side
retention policy; the data-URI OCR request does not create a provider file resource for Blowhard to
delete.

## Deliberate limitations

- The local parser verifies a conservative structural subset; it does not render, decompress
  pixels, execute PDF content, or resolve external resources.
- Valid PDFs using encryption, object streams, or page structures outside the explicit subset are
  rejected.
- The source panel shows verified metadata and page identity, not a rendered PDF or bitmap.
- One explicitly selected PNG or JPEG can be attached to the next conversation attempt through the
  separately documented image-question boundary. PDFs, multiple images, indexing, and search remain
  unsupported.
- Desktop has a picker implementation; mobile clients can reuse the lifecycle after supplying their
  own platform picker and storage composition.

These constraints keep the implemented security and privacy claims aligned with tested behavior.
