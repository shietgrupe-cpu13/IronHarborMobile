# Android launcher baseline

Iron Harbor Mobile is an unofficial fork based on [YuriPlanet/vera20k](https://github.com/YuriPlanet/vera20k). It retains upstream GPLv3 notices and attribution, uses its own product name and application ID (`org.ironharbor.mobile`), and is not an official release. It is not affiliated with Electronic Arts. Game installation files are supplied by the user and are not packaged with the application.

## Launcher flow

`LauncherActivity` is a platform Android Views activity, built without introducing a second UI framework. It uses a landscape-first, dark command-center layout with green accents and touch-sized controls. Its six sections are:

- **Home:** Iron Harbor Mobile branding, upstream engine attribution, build status, installation readiness, configured game path, and Play.
- **Game Files:** Android Storage Access Framework folder selection, required archive checks, import progress, cancellation, and safe replacement.
- **Game Settings:** reports landscape handling, Vulkan, surface-based resolution, engine audio defaults, and which controls are not yet wired.
- **Touch Controls:** describes the gesture mapping; sensitivity and overlay editing are unavailable.
- **Mods:** states that mod profiles are not supported in this build.
- **Diagnostics:** device/API/ABI and memory snapshot plus an export action. It packages the complete currently retained regular files in app-private `files/logs/` (including the current and rotated engine logs) and `files/except.txt` when present into a ZIP, then shares it with a temporary read-only content URI. No broad storage permission is used. Android system logcat is separate and is not included. Vulkan details and FPS/frame-time telemetry are not queried by the launcher.

Play is enabled only when the engine's configured directory contains both `ra2.mix` and `ra2md.mix`. Import validates those files before copying. It stages a complete recursive copy inside app-private storage and atomically publishes `config.toml` only on success. Cancel/failure leaves the previous configuration intact. No broad storage permission is requested. The check is a readiness check, not a full installation integrity audit.

Play starts the existing `VeraActivity` NativeActivity, which loads `libvera20k_android.so`. The native game activity has its own `:game` process, so the default-process Kotlin launcher remains available below it without creating a second winit event loop. The launcher refreshes installation status on resume and saves its selected page across activity recreation.

## Upstream and device references

The read-only visual/functional reference was `E:\RA2YRPORT\private-main-5bb0f38`, particularly `android/app/src/main/java/org/ra2/engine/SetupActivity.java`, touch settings/layout classes, and the import/diagnostics screens. Its dark card design, large Play action, readiness presentation, and grouped tools informed this launcher. The reference project itself was not modified.

The Android package uses only the original Iron Harbor vector mark and a neutral splash background. It excludes the upstream VERA icon, retail emblem, and `splash_hero` bitmap. `android/tools/make_launcher_icons.py` generates the Iron Harbor adaptive-icon XML only.

## Verification and limitations

Build b2 compiled the ARM64 Rust library using the optimized `fast` profile and packaged a debug APK. Build b3 uses the optimized `shipping` Rust profile and produces independently branded Iron Harbor Mobile debug and release APKs (`org.ironharbor.mobile`). Build b4 adds full retained-log ZIP export and increments version code to 208. Eight Android JVM tests pass, including archive-completeness and cache-path checks. APK v2 signatures, 16 KB ZIP alignment, and `0x4000` ELF LOAD alignment pass. The package is ARM64-only and targets Android API 36. The export UI and Android content-provider handoff have not been exercised on hardware. See [the b4 log-export report](android-log-export-b4.md); detailed build logs remain local under `out/logs/`.

ADB currently sees no device. The launcher has not been installed or run on a POCO/Android 16 device, so its rendered appearance, SAF provider behavior, Vulkan initialization, audio, game launch/resume, touch response, and match behavior remain unverified on hardware. Continue/resume save detection, writable graphics/audio settings, a mod manager, live Vulkan information, frame telemetry, touch sensitivity, and overlay editing are unavailable in this baseline. No benchmark or FPS improvement is claimed.

The corresponding source is in the `android-development` branch. Preserve `LICENSE-GPL`, `LEGAL.md`, and upstream attribution when distributing it.
