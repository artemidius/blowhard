# Security and limitations

Blowhard treats security claims as testable boundaries, not as a promise that a portfolio preview is production-ready.

## Implemented controls

- API credentials never enter observable UI state, logs, evaluation reports, diagnostics, or repository files.
- macOS stores the Mistral credential in Keychain. Windows and Linux fail closed because equivalent secure adapters
  are not implemented yet.
- Provider traffic uses a fixed HTTPS host; imported content is sent only after an explicit action.
- Local imports are structurally inspected and bounded before entering app-owned storage.
- Provider responses, tool calls, and stored payloads have explicit size and shape limits.
- Tools come from a fixed application registry. Model output cannot register a tool or choose arbitrary code, shell,
  file paths, network hosts, or SQL.
- Read tools are limited to attached document identities and label returned content as untrusted evidence.
- Mutations require an exact, expiring, single-use approval and show content plus destination before consent.
- Approved writes use atomic note-and-receipt persistence for deterministic, no-replay recovery.
- Diagnostics and run inspection omit prompts, responses, credentials, headers, native paths, and provider references
  by construction.
- CI runs with read-only repository permissions and no provider credentials. The quality gate scans Git history and
  the working tree for secrets.

## Data handling

Imported documents, OCR output, conversations, meeting notes, approvals that completed, and operation receipts are
stored locally in the application data directory. Removing Blowhard's local copy does not remove provider-side data.
Any content explicitly submitted to Mistral is subject to the provider's terms and retention policy.

The offline fixture benchmark uses synthetic data. Live evaluation is a separate, opt-in command that requires a
model, retrieves the credential from the secure platform store, limits case count and duration, and never writes the
credential to its report.

## Known limitations

- Native packages are not signed by a trusted publisher. The macOS package is ad hoc signed and not notarized;
  Windows and Linux packages are unsigned.
- Live Mistral use is currently supported only on macOS. Windows and Linux packages are useful for deterministic UI,
  packaging, and architecture review but cannot configure a live credential.
- Android and iOS have installable connection shells, and the iOS shell is launch-tested in CI, but neither has a
  native credential adapter or live provider path yet. They are unsigned development builds, not distributed
  applications.
- The tool protocol is intentionally narrow. It is not a general-purpose agent sandbox and never executes arbitrary
  commands.
- Local database encryption at rest is not implemented. Operating-system account and disk protections remain part of
  the threat model.
- A passing synthetic benchmark does not establish factual correctness on arbitrary private documents or future
  model versions.
- Publisher signing, notarization, telemetry, automatic update delivery, accessibility certification, penetration
  testing, and an external security audit are outside this preview.

Report suspected vulnerabilities privately through GitHub's security advisory interface. Do not place credentials,
private documents, or exploitable details in a public issue.
