#!/usr/bin/env bash

set -euo pipefail

./gradlew --no-daemon \
  :adapters:mobile-platform:connectedAndroidDeviceTest \
  :apps:android:connectedDebugAndroidTest

# Device-test infrastructure may remove the tested application during cleanup.
# Install once more so the startup measurement always targets a known artifact.
./gradlew --no-daemon :apps:android:installDebug

python3 tools/mobile/verify_android_startup.py \
  --package io.github.artemidius.blowhard \
  --component io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity \
  --max-total-time-ms 10000 \
  --output apps/android/build/reports/startup/cold-launch.json
