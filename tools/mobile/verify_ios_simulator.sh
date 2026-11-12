#!/usr/bin/env bash

set -euo pipefail

readonly app_path="${1:?usage: verify_ios_simulator.sh <app-path> <bundle-id>}"
readonly bundle_id="${2:?usage: verify_ios_simulator.sh <app-path> <bundle-id>}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS simulator verification requires macOS." >&2
  exit 1
fi

if [[ ! -d "$app_path" ]]; then
  echo "Application bundle not found: $app_path" >&2
  exit 1
fi

device_id="$({
  xcrun simctl list devices available
} | awk '/iPhone/ && /\((Shutdown|Booted)\)$/ { value = $(NF - 1); gsub(/[()]/, "", value); print value; exit }')"

if [[ -z "$device_id" ]]; then
  echo "No available iPhone simulator was found." >&2
  exit 1
fi

started_here=false
if ! xcrun simctl list devices | grep -F "$device_id" | grep -q '(Booted)'; then
  xcrun simctl boot "$device_id"
  started_here=true
fi

cleanup() {
  xcrun simctl terminate "$device_id" "$bundle_id" >/dev/null 2>&1 || true
  if [[ "$started_here" == true ]]; then
    xcrun simctl shutdown "$device_id" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

xcrun simctl bootstatus "$device_id" -b
xcrun simctl install "$device_id" "$app_path"
xcrun simctl launch "$device_id" "$bundle_id"
sleep 3
xcrun simctl spawn "$device_id" pgrep -x Blowhard >/dev/null

echo "Installed and launched $bundle_id on simulator $device_id."
