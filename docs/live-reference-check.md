# Reference live check

The macOS reference host can run one opt-in live check through the production Keychain credential,
Mistral model catalog, desktop generation router, and shared conversation presenter. Normal tests
and pull-request CI do not contact Mistral.

Run it only from a trusted local checkout with a configured Mistral key:

```shell
./gradlew :apps:desktop:referenceLiveSmoke --no-configuration-cache
```

The check requires `mistral-small-latest` in the account's verified chat catalog. It sends the
synthetic prompt “Reply with the single word READY.” once, limits output to 24 tokens, and has a
45-second overall timeout. It succeeds only when the shared conversation reaches a successful
terminal state with nonempty output. It does not print the key, prompt, response, account details,
or provider error body. A failed check exits nonzero with generic feedback. No conversation is
written to the user's history.

On 2026-11-11, the reference macOS host completed this check using a securely stored key. This is
one connection and generation smoke result, not an availability, model-quality, or billing claim.
Native package CI separately verifies installation and launch on macOS, Windows, and Linux with
an internal deterministic fixture.

For a macOS installer candidate, run the same check from the copied app in a temporary installation
directory. The second argument is deliberately opt-in and is never passed by CI:

```shell
check_root="$(mktemp -d)"
RUNNER_TEMP="$check_root" GITHUB_WORKSPACE="$PWD" \
  ./tools/packaging/verify_macos_package.sh . --live
rmdir "$check_root"
```

The package check verifies the DMG, copies its app bundle, runs the internal package fixture, then
launches the copied app's own executable for the bounded live check. It cleans the temporary app
copy and mount point before returning. On 2026-11-11, this installed-app check passed on the
reference host. It validates the candidate executable and its credential access on that host;
it does not provide a trusted publisher signature or verify the eventual release workflow build.
