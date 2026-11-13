# Mobile architecture and verification

Android and iOS are native application hosts around the same Compose Multiplatform presentation
and portable application runtime. The hosts own operating-system resources; shared UI sees only
typed ports, immutable state, and opaque identities.

## Capability map

| Capability | Android boundary | iOS boundary | Evidence |
| --- | --- | --- | --- |
| Credential storage | Keystore AES-GCM envelope in backup-excluded preferences | Device-bound, non-synchronizing Keychain item | Host tests plus Android device and iOS simulator lifecycle tests |
| Conversation recovery | App-private SQLDelight SQLite | Data-protected SQLDelight SQLite | Real close/reopen, interrupted-run, and corrupt-store tests |
| Document import | Storage Access Framework content URI | Security-scoped document URL | Bounded readers, structural inspection, durable restart tests |
| OCR and meeting notes | Verified-model Mistral adapters | Verified-model Mistral adapters | Portable mapping tests and native composition tests |
| Approved local copy | Shared exact-consent policy, native store | Shared exact-consent policy, native store | Single-use/cancellation tests and restart persistence |
| Diagnostic export | `CreateDocument` destination URI | Export-as-copy document picker | Native completion, dismissal, failure, and cleanup tests |
| Complete local reset | Credential plus app database | Credential plus app database | Independent-target and replacement-session tests |

No native path, URI, Keychain result, Keystore exception, or platform error enters portable UI
state. Optional storage capabilities fail closed rather than silently becoming ephemeral.

## Android

Build host evidence, then use the checked-in device procedure on a connected emulator or device:

```shell
./gradlew \
  :apps:android:assembleDebug \
  :apps:android:testDebugUnitTest

./tools/mobile/verify_android_startup_ci.sh
```

The device procedure runs both instrumented suites, reinstalls the application after test cleanup,
and records startup evidence. Keeping that reinstall explicit prevents the test runner's package
cleanup from invalidating the launch check.

After an explicit install, the content-free cold-start verifier can also be run independently:

```shell
python3 tools/mobile/verify_android_startup.py \
  --package io.github.artemidius.blowhard \
  --component io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity \
  --max-total-time-ms 10000 \
  --output apps/android/build/reports/startup/cold-launch.json
```

The 10-second ceiling is a regression tripwire for shared CI runners, not a product latency claim.
The report allowlists package/component identity, Android's launch state and timing fields, and the
declared ceiling. It contains no credential, content, native path, device identifier, or provider data.

## iOS

Run portable and native simulator tests, then build the unsigned simulator application:

```shell
./gradlew \
  :apps:ios-shared:checkKotlinAbi \
  :apps:ios-shared:iosSimulatorArm64Test \
  :shared-ui:iosSimulatorArm64Test

xcodebuild \
  -project apps/ios/Blowhard.xcodeproj \
  -scheme Blowhard \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath apps/ios/build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build
```

CI additionally verifies the reviewed default-data-protection entitlement, installs the app into an
iPhone simulator, launches it, and checks that the process remains alive. `simctl launch` does not
provide an equivalent app-ready duration, so no iOS startup number is fabricated.

## Deliberate limits

- Play Store and TestFlight delivery are not configured.
- Production mobile certificates and signing are not configured.
- Automated accessibility semantics are checked, but certification and a complete manual
  TalkBack/VoiceOver audit are not claimed.
- Android emulator and iOS simulator coverage is automated; physical iPhone validation is not claimed.
