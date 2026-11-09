#!/usr/bin/env bash
set -euo pipefail

repository_root="${1:-.}"
package_file="$(find "$repository_root/apps/desktop/build/compose/binaries/main/deb" -maxdepth 1 -type f -name '*.deb' -print -quit)"
test -n "$package_file"

architecture="$(dpkg-deb --field "$package_file" Architecture)"
test "$architecture" = "amd64"

extracted_root="$RUNNER_TEMP/blowhard-deb"
rm -rf "$extracted_root"
mkdir -p "$extracted_root"
dpkg-deb --extract "$package_file" "$extracted_root"
python3 "$repository_root/tools/packaging/verify_distribution.py" \
  "$extracted_root" \
  --forbid-text "$GITHUB_WORKSPACE" \
  --forbid-text "$RUNNER_TEMP"

cleanup() {
  sudo dpkg --remove blowhard >/dev/null 2>&1 || true
}
trap cleanup EXIT

sudo dpkg --install "$package_file"
launcher="$(dpkg-query --listfiles blowhard | awk '/\/bin\// && !/\/runtime\// { print; exit }')"
test -x "$launcher"

PATH=/usr/bin:/bin BLOWHARD_PACKAGING_SMOKE_TEST=1 xvfb-run --auto-servernum "$launcher"
echo "Installed and launched $package_file ($architecture)"
