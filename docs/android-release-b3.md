# Android build b3 report

Build date: 2026-11-18. Fork branch: `android-development`.

## Build and verification

- Rust library: `aarch64-linux-android` compiled with the repository's optimized `shipping` profile, `--locked`, and symbol stripping. Build completed successfully; Cargo reported 22m 37s.
- Android: Gradle 8.13, AGP 8.11.1, JDK 17, SDK/API 36, build-tools 36.1.0, NDK 28.2.13676358.
- Tests: `:app:testDebugUnitTest` passed six JVM tests (three import-job tests and three launcher-installation tests); `:app:assembleDebug` and `:app:assembleRelease` succeeded; release lint checks passed.
- Signing: both APKs passed `apksigner verify`. Release signature uses APK Signature Scheme v2, with certificate SHA-256 `7440ad6a732f410f570b55b8d7e0138b0a9ab318ce5553afc02e8e0527ce1711`.
- Alignment: both APKs passed `zipalign -c -P 16 -v 4`; every ARM64 ELF LOAD segment is aligned to `0x4000` (16 KB).
- Manifest: application ID `org.ironharbor.mobile`, label `Iron Harbor Mobile`, version `0.3.6` (version code 207), min SDK 26, target SDK 36, ARM64 only.
- APK contents: `lib/arm64-v8a/libvera20k_android.so` is present. No Yuri's Revenge `.mix`, `.ini`, `.pcx`, `.aud`, `.vqa`, or `.shp` game-data files were found.

## Three focused b3 fixes

1. **Independent fork identity and artwork:** Changed the Android product label and app ID to Iron Harbor Mobile, replaced upstream launcher artwork with an original vector mark, and retained upstream attribution and GPL notices. The previous product identity/artwork was unsuitable for distributing a modified fork under the upstream project's `LEGAL.md` guidance.
2. **Release signing:** Added an environment-driven release signing configuration and generated an ignored local signing key. Previously only debug signing was configured. The private keystore and its credentials are machine-local under `android/.android/` and are not in Git; keep secure backups together. This self-signed APK is appropriate for sideloading/testing, not a Play App Signing upload.
3. **ABI declaration:** Restricted the app to `arm64-v8a`, matching the only native library built by this recipe. Gradle had reported that an x86_64 library was missing while x86_64 was still declared.

## APK artifacts

- `out/apks/IronHarborMobile-0.3.6-android-arm64-debug-b3.apk` — SHA-256 `CA4C67C3F7802CC6161325B53D3E596F8C25BA8E1C9EA909F5048B8E6AC59CEA`.
- `out/apks/IronHarborMobile-0.3.6-android-arm64-release-b3.apk` — SHA-256 `74AC1A3B8239E6F5CCDB0CEBC5EB36E801CD42E5EC6F510DD60A4705B634C623`.

## Compatibility and remaining validation

`adb devices -l` found no connected Android device. The APK was not installed or exercised on a device during b3. Android 16/POCO launch and resume, SAF providers, Vulkan, audio, touch mapping, skirmish play, FPS/frame-time, memory, and input-latency behavior are therefore unverified for this build. No performance claim or benchmark is made.

Gradle emitted a source-manifest deprecation warning for `extractNativeLibs` and an SDK XML version mismatch warning; neither blocked packaging. Kotlin emitted deprecation warnings for existing system UI and task-description APIs. Logs are retained on the build machine under `out/logs/`.
