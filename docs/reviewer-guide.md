# Five-minute reviewer guide

Blowhard is a portfolio application built to make engineering decisions easy to inspect. The shortest useful review
is the meeting-note journey: a local document becomes an editable, evidence-linked note and an explicit approval
creates an independently stored local copy.

## 1. Start with the boundaries

The project keeps portable contracts in `core:api` and `core:domain`, application policy in `core:application`, and
provider, persistence, and platform details in adapters and platform composition roots. The portable
`app-composition` module owns shared application services and presenters without selecting native implementations.
Desktop, Android, and iOS assemble that same runtime outside UI recomposition; incomplete mobile capabilities are
explicitly fail-closed. Gradle rejects forbidden module dependencies.

Read these representative files:

- `core/application/.../DefaultMeetingNotes.kt` — application-owned generation and validation;
- `core/application/.../BoundedToolOrchestrator.kt` — finite tool-loop state machine;
- `core/application/.../ToolApprovalGate.kt` — consent bound to an exact run, call, arguments, target, and expiry;
- `app-composition/.../ApplicationRuntime.kt` — shared service assembly and deterministic lifetime ownership;
- `adapters/persistence/.../SqlDelightMeetingNoteStore.kt` — transactional note and receipt persistence;
- `apps/desktop/.../DesktopMeetingNoteApprovalPort.kt` — desktop composition without leaking platform details into UI.

## 2. Follow one user journey

1. Import a PDF, PNG, or JPEG into app-owned storage.
2. Open the document and, when needed, explicitly request OCR.
3. Generate structured meeting notes with a verified Mistral model.
4. Edit the typed title, decisions, and action items while retaining source references.
5. Select **Approve local copy** and inspect the exact content and destination.
6. Approve or deny the operation. Approval is single-use; denial writes nothing.

The [showcase architecture](architecture/meeting-note-showcase.md) maps every step to its trust boundary and tests.

## 3. Inspect measurable evidence

The checked-in [fixture report](evaluation/meeting-note-fixture-v1.md) is produced from a versioned 12-case synthetic
dataset. It reports schema validity, field quality, citation resolution, and semantic support with independent
denominators. The [methodology](evaluation/meeting-note-methodology.md) documents what the benchmark does and does
not claim. A dated [one-case live reference](evaluation/meeting-note-live-reference-2026-11-12.md) is also checked in;
its failed metrics and missing usage data are retained rather than polished away.

Reproduce it without credentials or a network call:

```shell
./gradlew :apps:evaluation:run
```

## 4. Run the engineering gate

```shell
./gradlew --no-configuration-cache qualityCheck
```

This runs formatting, static analysis, multiplatform tests, coverage enforcement, module-boundary checks, binary API
validation, KDoc generation, dependency-license policy, SQL migration verification, and secret scanning of both Git
history and the working tree.

## 5. Check the public delivery evidence

Pull requests run Linux, Windows, macOS, Android, and Apple targets with read-only permissions and no provider
credentials. Desktop packages and both mobile application shells are built, installed, and launched; desktop release
artifacts are also inventoried and bound to their source revision by SHA-256 evidence. Signing and mobile capability
limitations are disclosed in the
[security and limitations](security.md) document rather than hidden behind a successful build badge.
