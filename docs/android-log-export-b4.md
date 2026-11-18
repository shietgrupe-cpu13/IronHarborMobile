# Android full-log export — build b4

## Changes

1. The Diagnostics action now builds a ZIP with the complete retained regular files in app-private `files/logs/` (including `ra2.log` and `ra2.log.1` when present), the device summary and contents inventory, plus `files/except.txt` when present. It does not export unrelated app-private files or system logcat.
2. A non-exported read-only `ContentProvider` grants temporary URI access only to generated ZIPs in the app cache. The share Intent attaches the archive and includes a chooser grant; no broad storage permission is requested.
3. ZIP creation runs on a worker thread, and the export control shows a preparing state and reports failures/empty logs. The APK version code advances from 207 to 208.

## Verification

- Gradle `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease` succeeded.
- Eight JVM tests passed: three asset-import tests, three launcher-installation tests, and two archive/provider-path tests. The new archive test compares a 2 MiB+ log byte-for-byte and checks rotated-log and panic-report entries; the path test rejects traversal and missing files.
- The merged manifest contains `org.vera20k.DiagnosticArchiveProvider` at `org.ironharbor.mobile.diagnostics`, `exported=false`, and `grantUriPermissions=true`.
- Package ID is `org.ironharbor.mobile`, version `0.3.6`, version code 208, min SDK 26, target SDK 36, ARM64 only.
- Debug and release APK signatures verify. Both pass `zipalign -c -P 16 -v 4`. The ARM64 native library is included and unchanged from b3's optimized shipping build.
- No game installation files were found in the APK.

## Artifacts

- Debug: `out/apks/IronHarborMobile-0.3.6-android-arm64-debug-b4.apk` — SHA-256 `003F637033BD5FCB8077BD27F6AF8F3236EC64161DF3A43932D0F5AFBAF915DE`.
- Release: `out/apks/IronHarborMobile-0.3.6-android-arm64-release-b4.apk` — SHA-256 `862A4C80EDF17DF422BCC452C265F64549D31104021832E61D7FA3E77C2AF4FF`.

## Limits

No device was connected. The Android Sharesheet handoff and reading the exported ZIP in a receiving app remain unverified on hardware. The export contains app-owned engine logs and a panic report; Android's system logcat is separate and is not bundled. No gameplay or performance change is included in b4.
