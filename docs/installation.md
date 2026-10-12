# Install a desktop candidate

Blowhard does not publish a general-availability release yet. Native packages attached to CI runs
are short-lived verification candidates built from a specific commit. A manually approved release
workflow can also assemble the same evidence into a draft prerelease. Use a package only when you
trust the workflow and source revision that produced it.

Each candidate evidence archive contains the installer, `manifest.json`, `SHA256SUMS`, and a
host-specific dependency/license report. The public product version is `0.1.0`; native filenames
currently use installer schema `1.0.0` because the macOS packager requires a positive major version.

## Verify before installation

Confirm that the manifest names the expected source revision, operating system, architecture, and
signing status. Then verify the installer bytes from the directory containing `SHA256SUMS`.

macOS and Linux:

```shell
shasum -a 256 -c SHA256SUMS
```

On Linux, `sha256sum --check SHA256SUMS` is equivalent. Windows PowerShell users can compare the
displayed value with `SHA256SUMS`:

```powershell
Get-FileHash -Algorithm SHA256 .\Blowhard-1.0.0.msi
```

A matching checksum proves integrity relative to the downloaded evidence; it does not authenticate
the publisher. Current candidates have no trusted publisher signature. macOS candidates are ad hoc
signed and not notarized, while Windows and Linux candidates are unsigned. Do not disable operating
system security controls to install an untrusted candidate.

## Platform instructions

| Platform | Candidate | Installation | Current capability |
| --- | --- | --- | --- |
| macOS 15, Apple silicon | `.dmg` | Open the image and copy `Blowhard.app` to Applications | Keychain-backed live Mistral configuration |
| Windows x64 | `.msi` | Run the per-user installer; uninstall through Installed apps | Installation verification; credential storage unavailable |
| Ubuntu 24.04, amd64 | `.deb` | `sudo apt install ./path-to-candidate.deb` | Installation verification; credential storage unavailable |

Exact filenames are recorded in the candidate manifest. Windows and Linux intentionally fail
closed for live mode because their secure credential adapters have not been implemented.

On macOS, enter a Mistral API key when prompted. Blowhard stores it in Keychain and verifies the
connection through the model catalog. A failed check keeps the conversation inactive; retry the
check or replace the key in settings. Choose a verified model before sending a message. Windows
and Linux currently show that secure credential storage is unavailable, so the conversation stays
inactive on those hosts.

## Local data and removal

Uninstalling the application package does not claim to delete user-created data. Blowhard stores
configuration and conversation data under the platform configuration directory and nonessential
files under the cache directory:

| Platform | Configuration | Cache |
| --- | --- | --- |
| macOS | `~/Library/Application Support/Blowhard` | `~/Library/Caches/Blowhard` |
| Windows | `%APPDATA%\Blowhard` | `%LOCALAPPDATA%\Blowhard\Cache` |
| Linux | `$XDG_CONFIG_HOME/blowhard` or `~/.config/blowhard` | `$XDG_CACHE_HOME/blowhard` or `~/.cache/blowhard` |

Review those paths before removing them manually. On macOS, the Mistral credential is stored
separately in Keychain and should be deleted through Blowhard Settings before uninstalling.
