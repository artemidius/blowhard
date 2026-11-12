#!/usr/bin/env bash

set -euo pipefail

readonly ios_root="${1:?usage: verify_ios_data_protection.sh <ios-project-root>}"
readonly entitlements="$ios_root/Blowhard/Blowhard.entitlements"
readonly project="$ios_root/Blowhard.xcodeproj/project.pbxproj"
readonly expected_protection="NSFileProtectionComplete"
readonly expected_setting='CODE_SIGN_ENTITLEMENTS = Blowhard/Blowhard.entitlements;'

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS data-protection verification requires macOS." >&2
  exit 1
fi

if [[ ! -f "$entitlements" || ! -f "$project" ]]; then
  echo "iOS entitlement source or generated Xcode project is missing." >&2
  exit 1
fi

actual_protection="$(
  plutil -extract 'com\.apple\.developer\.default-data-protection' raw -o - "$entitlements"
)"
if [[ "$actual_protection" != "$expected_protection" ]]; then
  echo "Unexpected iOS data-protection class: $actual_protection" >&2
  exit 1
fi

setting_count="$(grep -Fc "$expected_setting" "$project")"
if [[ "$setting_count" -ne 2 ]]; then
  echo "Debug and release must both use the reviewed entitlements file." >&2
  exit 1
fi

echo "Verified $expected_protection for both iOS build configurations."
