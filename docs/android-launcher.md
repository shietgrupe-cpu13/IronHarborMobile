# Android launcher baseline

VERA20K Android is an unofficial fork of [YuriPlanet/vera20k](https://github.com/YuriPlanet/vera20k). It retains upstream GPLv3 notices and attribution. It is not an official release and is not affiliated with Electronic Arts. Game installation files are supplied by the user and are not packaged with the application.

## Launcher flow

`LauncherActivity` is a platform Android Views activity, built without introducing a second UI framework. It uses a landscape-first, dark command-center layout with green accents and touch-sized controls. Its six sections are:

- **Home:** VERA fork identification, build and engine status, installation readiness, configured game path, and Play.
- **Game Files:** Android Storage Access Framework folder selection, required archive checks, import progress, cancellation, and safe replacement.
- **Game Settings:** reports landscape handling, Vulkan, surface-based resolution, engine audio defaults, and which controls are not yet wired.
- **Touch Controls:** describes the gesture mapping; sensitivity and overlay editing are unavailable.
- **Mods:** states that mod profiles are not supported in this build.
- **Diagnostics:** device/API/ABI and memory snapshot, engine log location, and shareable text report. Vulkan details and FPS/frame-time telemetry are not queried by the launcher.

Play is enabled only when the engine's configured directory contains both `ra2.mix` and `ra2md.mix`. Import validates those files before copying. It stages a complete recursive copy inside app-private storage and atomically publishes `config.toml` only on success. Cancel/failure leaves the previous configuration intact. No broad storage permission is requested. The check is a readiness check, not a full installation integrity audit.

Play starts the existing `VeraActivity` NativeActivity, which loads `libvera20k_android.so`. The native game activity has its own `:game` process, so the default-process Kotlin launcher remains available below it without creating a second winit event loop. The launcher refreshes installation status on resume and saves its selected page across activity recreation.

## Upstream and device references

The read-only visual/functional reference was `E:\RA2YRPORT\private-main-5bb0f38`, particularly `android/app/src/main/java/org/ra2/engine/SetupActivity.java`, touch settings/layout classes, and the import/diagnostics screens. Its dark card design, large Play action, readiness presentation, and grouped tools informed this launcher. The reference project itself was not modified.

The Android package includes only the VERA icon for its launcher aliases and uses a neutral splash background. It excludes the upstream retail emblem and `splash_hero` bitmap. `android/tools/make_launcher_icons.py` generates the VERA icon only.

## Verification and limitations

Build b2 compiles the ARM64 Rust library using the optimized `fast` profile and packages a debug APK. The Android JVM suite reports six passing tests. APK v2/v3 signatures, 16 KB ZIP alignment, and `0x4000` ELF LOAD alignment pass. The package is ARM64-only and targets Android API 36. See `out/logs/android-launcher-baseline-report.md` and the associated build logs for the artifact hash and detailed checks.

ADB currently sees no device. The launcher has not been installed or run on a POCO/Android 16 device, so its rendered appearance, SAF provider behavior, Vulkan initialization, audio, game launch/resume, touch response, and match behavior remain unverified on hardware. Continue/resume save detection, writable graphics/audio settings, a mod manager, live Vulkan information, frame telemetry, touch sensitivity, and overlay editing are unavailable in this baseline. No benchmark or FPS improvement is claimed.

The corresponding source is in the `android-development` branch. Preserve `LICENSE-GPL`, `LEGAL.md`, and upstream attribution when distributing it.
