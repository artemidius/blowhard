#!/usr/bin/env bash
set -euo pipefail

repository_root="${1:-.}"
package_file="$(find "$repository_root/apps/desktop/build/compose/binaries/main/dmg" -maxdepth 1 -type f -name '*.dmg' -print -quit)"
test -n "$package_file"

mount_point="$RUNNER_TEMP/blowhard-dmg"
install_root="$RUNNER_TEMP/Applications"
rm -rf "$mount_point" "$install_root"
mkdir -p "$mount_point" "$install_root"

cleanup() {
  hdiutil detach "$mount_point" -quiet >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf 'Y\n' | hdiutil attach "$package_file" -nobrowse -readonly -mountpoint "$mount_point"
source_app="$(find "$mount_point" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -n "$source_app"
installed_app="$install_root/$(basename "$source_app")"
ditto "$source_app" "$installed_app"

python3 "$repository_root/tools/packaging/verify_distribution.py" \
  "$installed_app" \
  --forbid-text "$GITHUB_WORKSPACE" \
  --forbid-text "$RUNNER_TEMP"

launcher="$installed_app/Contents/MacOS/Blowhard"
test -x "$launcher"
binary_description="$(file "$launcher")"
grep -q 'Mach-O 64-bit executable arm64' <<<"$binary_description"
signing_details="$(codesign --display --verbose=4 "$installed_app" 2>&1)"
grep -q '^Signature=adhoc$' <<<"$signing_details"
grep -q '^TeamIdentifier=not set$' <<<"$signing_details"

PATH=/usr/bin:/bin:/usr/sbin:/sbin BLOWHARD_PACKAGING_SMOKE_TEST=1 "$launcher"
echo "Installed and launched $package_file (arm64; ad hoc signed, not Developer ID signed or notarized)"
