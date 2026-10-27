# Blowhard

[![CI](https://github.com/artemidius/blowhard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/artemidius/blowhard/actions/workflows/ci.yml)

A desktop-first Kotlin Multiplatform client for Mistral AI and an inspectable AI-engineering portfolio project.

The current milestone demonstrates an end-to-end document-to-meeting-note workflow: bounded local
import, explicit provider processing, structured evidence-linked output, typed editing, measurable
evaluation, scoped read tools, exact mutation approval, and crash-safe persistence. Dedicated
Android and iOS application shells render the shared connection workflow and fail closed until
their native credential adapters are installed.

If you are reviewing the repository, start with the
[five-minute reviewer guide](docs/reviewer-guide.md), the
[showcase architecture](docs/architecture/meeting-note-showcase.md), and the
[security disclosure](docs/security.md).

## Technical direction

- Kotlin Multiplatform and Compose Multiplatform.
- Streaming conversations, document workflows, and controlled AI tool execution.
- Small, focused modules with explicit contracts and platform-specific adapters.
- Security-conscious design, automated testing, static analysis, and documented APIs.
- Reproducible synthetic evaluation with deterministic machine- and human-readable reports.

The desktop application is the initial focus, with Android and iOS compatibility guiding
the shared architecture.

## Prerequisites

- JDK 21
- Android SDK 37 for Android compilation
- Xcode 16 or newer for the iOS 15 application target
- Gitleaks 8.30 or newer for the local security gate

The toolchain is pinned in the Gradle version catalog and documented in
[`ADR 0001`](docs/decisions/0001-toolchain-and-project-shape.md).

The shared client contract and enforced module direction are documented in
[`ADR 0002`](docs/decisions/0002-core-api-and-dependency-boundaries.md).

The local quality and security gate is documented in
[`ADR 0003`](docs/decisions/0003-quality-gates.md).

The cross-platform CI matrix and security model are documented in
[`ADR 0004`](docs/decisions/0004-continuous-integration.md).

Native package identity and verification are documented in
[`ADR 0005`](docs/decisions/0005-native-desktop-packaging.md).

Run ownership, concurrency, and deterministic fixture behavior are documented in
[`ADR 0006`](docs/decisions/0006-run-lifecycle-and-fixtures.md) and the
[run lifecycle guide](docs/architecture/run-lifecycle.md).

The unidirectional presentation model, safe Markdown subset, and desktop composition root are
documented in [`ADR 0007`](docs/decisions/0007-shared-conversation-presentation.md) and the
[conversation presentation guide](docs/architecture/conversation-presentation.md).

Native menus, clipboard and window-state boundaries, application directories, accessibility, and
the verified desktop support matrix are documented in
[`ADR 0008`](docs/decisions/0008-desktop-platform-integration.md) and the
[desktop platform support guide](docs/architecture/desktop-platform-support.md).

The first live-provider boundary discovers Mistral models through a provider-neutral catalog and a
hardened, fixed-host Ktor adapter. Its security and mapping decisions are documented in
[`ADR 0009`](docs/decisions/0009-mistral-model-catalog-boundary.md) and the
[Mistral provider boundary guide](docs/architecture/mistral-provider-boundary.md).

The same adapter module implements bounded, cancellation-aware Mistral chat streaming without
automatic POST replay. Its framing, terminal-state, and retry decisions are documented in
[`ADR 0010`](docs/decisions/0010-mistral-streaming-chat.md) and the
[streaming chat guide](docs/architecture/mistral-streaming-chat.md).

Secure credential lifecycle is isolated behind a provider-neutral store. The macOS implementation
uses native Keychain generic-password items and other desktop hosts currently fail closed. The
boundary and its limitations are documented in
[`ADR 0011`](docs/decisions/0011-macos-keychain-credentials.md) and the
[credential storage guide](docs/architecture/credential-storage.md).

Live-provider configuration is coordinated through a shared, secret-free settings contract. It
requires a verified compatible model. The desktop client discloses external processing before
credential entry and in the conversation. The policy is documented in
[`ADR 0012`](docs/decisions/0012-explicit-live-provider-settings.md) and the
[live-provider settings guide](docs/architecture/live-provider-settings.md).

The desktop client exposes that workflow through a shared settings screen and composes the macOS
Keychain with the Mistral model catalog. Credential entry remains outside observable presentation
state, while Windows and Linux continue to fail closed. See
[`ADR 0013`](docs/decisions/0013-provider-settings-presentation.md).

Explicit live routing snapshots the selected source and verified model into each accepted attempt,
uses the same run lifecycle for fixture and Mistral streams, and never falls back between them.
See [`ADR 0014`](docs/decisions/0014-immutable-live-generation-routing.md) and the
[live conversation routing guide](docs/architecture/live-conversation-routing.md).

User-initiated regeneration appends a distinct attempt while preserving the prior answer and its
provenance. It never replays automatically and follows conservative retry advice. See
[`ADR 0015`](docs/decisions/0015-explicit-regeneration-attempts.md).

Provider-neutral maximum-output and temperature controls are validated and snapshotted when an
attempt is accepted, then mapped to documented Mistral request fields. Model defaults remain
explicitly selectable. See
[`ADR 0016`](docs/decisions/0016-generation-parameter-snapshots.md).

Durable conversation metadata and transcript snapshots now have a provider-independent core port
and a SQLDelight/SQLite adapter compiled for desktop, Android, and iOS. The checked-in schema
baseline and versioned migration are verified during the quality gate, while desktop tests prove
close/reopen persistence, rollback, owned-row deletion, and a real version-1 upgrade. Desktop
startup now opens the app-owned database, recovers uncertain attempts, and displays stored history
without contacting a provider. See
[`ADR 0017`](docs/decisions/0017-sqldelight-conversation-storage.md) and the
[conversation persistence guide](docs/architecture/conversation-persistence.md).

Stored transcripts separate ordered messages from generation attempts. Each attempt retains its
run identity, message links, source/model target, generation controls, timestamps, terminal or
recoverable state, and sanitized failure details. See
[`ADR 0018`](docs/decisions/0018-atomic-transcript-snapshots.md).

Restart recovery is explicit and fail-closed: one atomic, idempotent pass marks queued and running
attempts interrupted while preserving partial output and immutable request snapshots. It never
replays provider work. Storage failures produce an explicit content-safe unavailable state rather
than silently substituting a disposable database. See
[`ADR 0019`](docs/decisions/0019-fail-closed-restart-recovery.md).

New desktop attempts now extend the durable transcript through application-owned checkpoints.
Queued state is committed before provider work, streaming writes are bounded by output growth or
elapsed time, and terminal outcomes are committed before publication. Metadata and transcript move
atomically, while storage failures stop safely without provider replay. See
[`ADR 0020`](docs/decisions/0020-desktop-history-startup.md) and
[`ADR 0021`](docs/decisions/0021-bounded-run-checkpoints.md).

Local deletion is an explicit, confirmed application operation. Active runs are fenced, the
conversation aggregate is removed atomically, and storage failures leave the conversation visible
without claiming success. Deletion affects Blowhard's local copy only: it does not modify the API
credential or provider-side retention. See
[`ADR 0022`](docs/decisions/0022-confirmed-local-conversation-deletion.md).

The first run-inspection boundary now records a bounded, content-free operational timeline for
each process-owned attempt. It allowlists routing, generation controls, lifecycle timing, terminal
classification, retry guidance, and optional provider token counts while omitting prompts,
responses, credentials, headers, and provider references by construction. Mistral streaming
requests explicitly ask for usage, and missing counts remain unknown rather than zero. See
[`ADR 0023`](docs/decisions/0023-bounded-content-free-run-inspection.md) and the
[run-inspection guide](docs/architecture/run-inspection.md).

Completed and active assistant attempts expose an **Inspect run** action in the shared UI. The
screen performs a passive lookup through the same provider-neutral core contract, renders only the
allowlisted inspection model, and offers an explicit refresh without starting or replaying provider
work. Restored attempts honestly report that process-local inspection data is unavailable.

The inspector also offers **Export JSON**. The application builds a deterministic, bounded report
from the content-free inspection model, then desktop asks the user for a destination with a native
save dialog and commits the exact UTF-8 file atomically. Cancellation writes nothing, and shared UI
state never contains a path or native error. A clearly synthetic
[schema example](docs/examples/sanitized-run-diagnostic.json) and
[`ADR 0024`](docs/decisions/0024-explicit-sanitized-diagnostic-export.md) document the boundary.

The local document library accepts explicitly selected PDF, PNG, and JPEG files through a
path-free shared contract. Imports are bounded, structurally inspected without trusting file
extensions, and stored atomically with stable identity in the app-owned database. The desktop UI
can list and confirm deletion of these copies; importing never starts a provider request or sends
content to Mistral. See [`ADR 0028`](docs/decisions/0028-bounded-local-document-lifecycle.md) and
the [document lifecycle guide](docs/architecture/document-lifecycle.md).

Each imported document can be opened in a shared workbench. **Extract with Mistral** is a separate,
explicit request that submits only that stored source to the fixed OCR endpoint. Request and
response sizes, page ordering, content type, and output shape are validated before the Markdown is
atomically persisted. Reopening the workbench reads local output without repeating provider work.
See [`ADR 0029`](docs/decisions/0029-explicit-bounded-document-ocr.md).

Verified vision models can receive one explicitly selected imported PNG or JPEG with the next
conversation prompt. The composer passes only an opaque document identity; the application checks
the captured model capability before reading local storage, and the Mistral adapter constructs one
bounded data URI without exposing a native path or accepting a remote URL. Rejected starts restore
the selection, while regeneration never resubmits an image implicitly. See
[`ADR 0030`](docs/decisions/0030-explicit-local-image-questions.md) and the
[image-question guide](docs/architecture/image-questions.md).

## Run the desktop application

```shell
./gradlew :apps:desktop:run
```

On macOS, Blowhard asks for a Mistral API key before opening the conversation. The key is stored
in Keychain. The application checks the model catalog at startup and after a key is saved; a failed
check keeps the conversation inactive and displays a safe error. Choose a verified model in
**Provider settings**, reachable from the conversation header. Generation controls offer bounded
maximum-output and temperature presets for subsequently accepted attempts. Messages you send are
processed by Mistral.

Use **Delete** in the conversation header to remove local history after reviewing the confirmation
boundary. Stop an active run first. When the last conversation is deleted, the shared client
creates a fresh empty workspace without recreating the deleted database record.

Use **Local documents** in the desktop sidebar to import one PDF, PNG, or JPEG into Blowhard's
app-managed storage. The 20 MiB, 100-page, and 40-megapixel limits are checked locally. Deleting an
imported copy does not modify the originally selected file.

Open a stored document to inspect its verified source metadata and any durable OCR output. Choose
**Extract with Mistral** to submit it; local import alone never uploads content. Successful output
is grouped by page and retained with the app-owned copy until that document is deleted.

With a verified vision model selected, choose **Attach local image** in the composer to send one
imported PNG or JPEG with the next prompt. The selection is explicit and one-shot. Blowhard does not
attach PDFs, send every imported file, accept provider-fetched image URLs, or reuse an image during
regeneration.

For PDFs with extracted text, choose **Generate meeting notes** in the document workbench. Blowhard
requests a strict structured response, validates source references, and presents typed title,
decision, action-item, and evidence fields for editing. **Approve local copy** opens a separate consent
step showing the exact content and destination. Denial writes nothing; approval is single-use and
the local write is paired atomically with a recovery receipt.

The internal tool protocol is deliberately narrow. `search_text` and `read_page` can read only the
documents attached to the run, under hard result and orchestration limits. Model output cannot
register tools, escape document scope, or authorize a mutation. See the
[meeting-note architecture](docs/architecture/meeting-note-showcase.md),
[`ADR 0031`](docs/decisions/0031-bounded-tool-orchestration.md), and
[`ADR 0032`](docs/decisions/0032-exact-consent-and-no-replay-writes.md).

## Run the mobile shells

Install the Android debug application on a connected API 26 or newer device or emulator:

```shell
./gradlew :apps:android:installDebug
```

Open `apps/ios/Blowhard.xcodeproj` in Xcode to run the iOS 15 or newer application on an Apple
silicon simulator or device. The Xcode build invokes the narrow `apps:ios-shared` Kotlin framework
automatically. These initial hosts expose the real shared connection UI but intentionally cannot
store a credential or make a provider call yet.

Reproduce the 12-case synthetic meeting-note evaluation without a credential or network call:

```shell
./gradlew :apps:evaluation:run
```

The checked-in [fixture report](docs/evaluation/meeting-note-fixture-v1.md) and
[methodology](docs/evaluation/meeting-note-methodology.md) disclose the independent scoring
denominators and limitations. Live evaluation is a separate bounded opt-in mode; invoke it with
explicit arguments so the model, case limit, output location, and secure credential requirements
remain reviewable:

```shell
./gradlew :apps:evaluation:run \
  --args='--live --model MODEL_ID --max-cases 1 --output build/reports/evaluation-live'
```

The dated [one-case live reference](docs/evaluation/meeting-note-live-reference-2026-11-12.md)
preserves both passes and failures and explicitly states that no human semantic review was performed.

Use `Command+Enter` on macOS or `Ctrl+Enter` on Windows and Linux to send from the composer. Native
menus expose New conversation, Send, Stop, Regenerate latest response, Copy latest response, and
Exit with platform-appropriate shortcuts. Every non-empty message has a visible copy action.
Terminal responses that are safe to repeat expose **Regenerate with current mode** and retain both
attempt identities and outputs.

## Verify the foundation

```shell
./gradlew --no-configuration-cache qualityCheck
```

The aggregate gate checks formatting, static analysis, tests, coverage thresholds, module
boundaries, API compatibility, KDoc generation, desktop runtime licenses, and secrets in both Git
history and the working tree. Apply safe formatting changes with `./gradlew spotlessApply`.
The explicit cache flag reflects the license-report plugin's documented configuration-cache
limitation; ordinary compilation and test tasks continue to use the repository cache settings.
CI additionally runs a checksum-verified actionlint binary against every GitHub Actions workflow.

Generated API documentation is written to `build/dokka/html`, and the XML coverage report is
written to `build/reports/kover/report.xml`.

GitHub Actions builds and tests the desktop JVM application on pinned Linux, Windows, and macOS
runners. Separate jobs build, lint, install, launch, and inspect the Android application and compile,
install, and launch the iOS application with shared simulator tests. The workflow uses read-only
permissions and no provider credentials.

On macOS, also verify the shared iOS target:

```shell
./gradlew :apps:ios-shared:compileKotlinIosSimulatorArm64
```

## Native desktop packages

Native installers are built only on their corresponding operating systems because Compose does
not support cross-packaging:

| Host | Package | Architecture | Gradle task |
| --- | --- | --- | --- |
| macOS | `.dmg` | Apple silicon (`arm64`) | `:apps:desktop:packageDmg` |
| Windows | `.msi` | `x64` | `:apps:desktop:packageMsi` |
| Linux | `.deb` | `amd64` | `:apps:desktop:packageDeb` |

Run packaging tasks with `--no-configuration-cache`; the Compose packaging plugin does not yet
support Gradle's configuration cache for these tasks. Each package contains a minimized Java
runtime and does not require a separately installed JDK.

CI builds each native format, inspects its contents, installs it, and launches the installed
Compose application in a deterministic smoke mode. Pull-request archives are retained with the CI
run for seven days; reviewed milestone packages are available from
[GitHub Releases](https://github.com/artemidius/blowhard/releases). Each package is accompanied by a deterministic
manifest and `SHA256SUMS` that bind its bytes to release `0.2.0`, installer schema `1.0.0`, the exact
source revision, target architecture, and disclosed signing status. See the
[release-evidence guide](docs/release-evidence.md) and
[`ADR 0025`](docs/decisions/0025-deterministic-release-evidence.md).

Each host also resolves the packaged desktop runtime graph, normalizes declared licenses to SPDX
identifiers, enforces the reviewed allowlist, and uploads deterministic JSON and Markdown
inventories. See the [dependency and license inventory guide](docs/dependency-license-inventory.md)
and [`ADR 0026`](docs/decisions/0026-runtime-license-inventory.md).

The current packages have no trusted publisher signature. `jpackage` applies an ad hoc signature
to the macOS app, but it is not Developer ID signed or notarized. The Windows package is not
publisher-signed. The `v0.2.0` prerelease is intended for source-bound portfolio evaluation on the
documented targets; trusted distribution requires publisher signing and notarization.

macOS is the current live reference host. Windows and Linux packages currently cannot start a
conversation because a verified secure credential adapter is not implemented for those hosts.
Automated tests use synthetic provider fixtures; a bounded macOS
[live reference check](docs/live-reference-check.md) has also passed through a locally installed
DMG candidate. The reviewed [`v0.2.0` prerelease](https://github.com/artemidius/blowhard/releases/tag/v0.2.0)
contains the three native packages and their verification evidence.

The [installation guide](docs/installation.md) documents package verification, native install
steps, signing limitations, capability boundaries, and local data locations.

Release candidates are assembled only by the manually approved, least-privilege
[protected release process](docs/release-process.md). The workflow rebuilds immutable `main` source
on all three desktop hosts and creates a draft prerelease for human review; see
[`ADR 0027`](docs/decisions/0027-protected-draft-publication.md).

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the review and verification workflow.

Blowhard is an independent project and is not affiliated with Mistral AI.

## License

Blowhard is open-source software available under the permissive [MIT License](LICENSE).
