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

find_available_iphone() {
  xcrun simctl list devices available |
    sed -nE 's/.*\(([0-9A-Fa-f-]{36})\) \((Booted|Shutdown)\).*/\1/p' |
    head -n 1
}

create_iphone() {
  local device_type_id
  local runtime_id

  device_type_id="$(
    xcrun simctl list devicetypes |
      sed -nE 's/^iPhone[^()]*(\(com\.apple\.CoreSimulator\.SimDeviceType\.[^)]+\)).*/\1/p' |
      tr -d '()' |
      head -n 1
  )"
  runtime_id="$(
    xcrun simctl list runtimes available |
      sed -nE 's/^iOS.* - (com\.apple\.CoreSimulator\.SimRuntime\.iOS-[^[:space:]]+).*/\1/p' |
      head -n 1
  )"

  if [[ -z "$device_type_id" || -z "$runtime_id" ]]; then
    echo "No compatible iPhone device type and iOS runtime were found." >&2
    return 1
  fi

  xcrun simctl create "Blowhard CI" "$device_type_id" "$runtime_id"
}

device_id="$(find_available_iphone)"
created_here=false

if [[ -z "$device_id" ]]; then
  device_id="$(create_iphone)"
  created_here=true
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
  if [[ "$created_here" == true ]]; then
    xcrun simctl delete "$device_id" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

xcrun simctl bootstatus "$device_id" -b
xcrun simctl install "$device_id" "$app_path"
xcrun simctl launch "$device_id" "$bundle_id"
sleep 3
xcrun simctl spawn "$device_id" pgrep -x Blowhard >/dev/null

echo "Installed and launched $bundle_id on simulator $device_id."
