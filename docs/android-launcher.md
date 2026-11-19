# Android launcher baseline

Iron Harbor Mobile is an unofficial fork based on [YuriPlanet/vera20k](https://github.com/YuriPlanet/vera20k). It retains upstream GPLv3 notices and attribution, uses its own product name and application ID (`org.ironharbor.mobile`), and is not an official release. It is not affiliated with Electronic Arts. Game installation files are supplied by the user and are not packaged with the application.

## Launcher flow

`LauncherActivity` is a platform Android Views activity, built without introducing a second UI framework. It uses a landscape-first, dark command-center layout with green accents and touch-sized controls. Its six sections are:

- **Home:** Iron Harbor Mobile branding, upstream engine attribution, build status, installation readiness, configured game path, and Play.
- **Game Files:** Android Storage Access Framework folder selection, required archive checks, import progress, cancellation, and safe replacement.
- **Game Settings:** reports landscape handling, Vulkan, surface-based resolution, engine audio defaults, and which controls are not yet wired.
- **Touch Controls:** retains the engine's touch gestures and adds the separate floating command HUD, visibility, scale, opacity, and action-key editor.
- **Mods:** states that mod profiles are not supported in this build.
- **Diagnostics:** device/API/ABI and memory snapshot plus an export action. It packages the complete currently retained regular files in app-private `files/logs/` (including the current and rotated engine logs) and `files/except.txt` when present into a ZIP, then shares it with a temporary read-only content URI. No broad storage permission is used. Android system logcat is separate and is not included. Vulkan details and FPS/frame-time telemetry are not queried by the launcher.

Play is enabled only when the engine's configured directory contains both `ra2.mix` and `ra2md.mix`. Import validates those files before changing the configuration. On Android 11+ when the user has granted the app's optional all-files access and selected a folder on primary shared storage, the launcher configures that real folder directly; it does not duplicate the game installation. SAF-only, removable-volume, and cloud-provider selections retain the existing staged private copy and mirror fallback. The check is a readiness check, not a full installation integrity audit.

Play starts the existing `VeraActivity` NativeActivity, which loads `libvera20k_android.so`. The native game activity has its own `:game` process, so the default-process Kotlin launcher remains available below it without creating a second winit event loop. The launcher refreshes installation status on resume and saves its selected page across activity recreation.

## Upstream and device references

The read-only visual/functional reference was `E:\RA2YRPORT\private-main-5bb0f38`, particularly `android/app/src/main/java/org/ra2/engine/SetupActivity.java`, `TouchSettings.java`, `TouchLayout.java`, and the import/diagnostics screens. Its dark card design, large Play action, readiness presentation, and grouped tools informed this launcher. The reference project itself was not modified.

The Android package uses only the original Iron Harbor vector mark and a neutral splash background. It excludes the upstream VERA icon, retail emblem, and `splash_hero` bitmap. `android/tools/make_launcher_icons.py` generates the Iron Harbor adaptive-icon XML only.

## Verification and limitations

Build b12 increments Android `versionCode` to 216 and adds an RA2YRPORT-inspired floating HUD. Its STOP, GUARD, DEPLOY, SCATTER, and C1–C10 buttons are separate nonmodal application-panel windows above the NativeActivity; each button occupies only its own touch area so blank screen touches remain available to the Rust gesture mapper. Button taps dispatch ordinary NativeActivity key events, C1–C10 select groups, and holding a group button assigns the current selection. In-game **Edit HUD** allows drag positioning and per-button size cycling; launcher controls cover visible buttons, global scale, opacity, and remapping the four action keys. HUD layout and key choices persist in app preferences. The launcher does not request overlay permission.

The b12 Android JVM suite has 20 tests with no failures, including layout sanitization, button visibility, size/opacity bounds, stock action-key codes, and control-group key slots. Gradle builds the ARM64 APK. No Android device was connected for this increment, so panel touch-through, visual placement, group actions, hardware-keyboard remapping, and behavior on the POCO remain unverified on device. Layout import/export and a control-mode switch between touch-only and external mouse/keyboard are not in b12; the existing touch gesture mapper remains unchanged. In-game text still has no Android soft-keyboard integration.

## b13 floating HUD increment

Build b13 responds to the RA2YRPORT HUD comparison. The earlier Android overlay only exposed four command buttons and C1–C10, used one free placement arrangement, had no in-game fold control, and could not transfer layouts. The b13 launcher/overlay adds:

- The RA2YRPORT command palette IDs (24 actions including common commands, mission shortcuts, Yuri actions, build, and patrol). The four known stock key bindings remain available. Other commands are deliberately unbound until the player captures the key already assigned in the game; an unbound button logs and sends no fake key.
- Two-thumb and Yuri-inspired initial layouts, while retaining the user-positioned free layout. Button positions continue to be individually draggable and saved.
- An in-game HIDE HUD / SHOW HUD control that folds and restores the floating buttons without changing the engine's touch gesture mapper.
- Versioned `.ihhud` export/import through Android's document picker. It includes button visibility, positions, sizes, layout, and saved keyboard bindings. The importer validates the format, control IDs, key codes, normalized positions, sizes, file size (64 KB max), and scalar bounds before changing preferences; no broad storage permission is requested.

This adapts the reference's useful configuration behaviors to VERA20K's NativeActivity/key event model. It does not copy RA2YRPORT's native command injection or guarantee every reference command is implemented by VERA20K. A command only works when bound to a key VERA20K already handles. Per-action control-set toggles were added in b14; exact visual parity and explicit action-order editing remain future work, subject to VERA20K's supported commands and touch model.

The b13 Android JVM suite passes all 22 tests, including preset position bounds and layout backup validation. The launcher-only Gradle build completed in 27 seconds with the b12 ARM64 Rust library reused. The APK is signed with the Android debug key, package `org.ironharbor.mobile`, version `0.3.6` / versionCode `217`, and contains `lib/arm64-v8a/libvera20k_android.so`. `zipalign -c -P 16 4` and APK v2 signature verification pass; ELF LOAD segments are aligned to `0x4000`; no game installation files are bundled. See `out/logs/android-b13-report.md` for the build record. No Android device was connected, so HUD hit testing, layout rendering, import/export picker behavior, command dispatch, and POCO/Android 16 behavior remain unverified on hardware.

## b14 control-set increment

The RA2YRPORT reference also supports separating commands into two switchable sets. b14 adds an `Always / Set 1 / Set 2` assignment for each command in the launcher editor and adds in-game Set 1 / Set 2 selectors. Control-set assignments are included in HUD backup version 2; the importer continues to accept version 1 backups and treats their commands as Always visible. Control groups C1–C10 remain independent and continue to select/assign engine groups. The Rust engine and simulation are unchanged.

The b14 Android JVM suite passes 22 tests, including control-set filtering, round-trip persistence, invalid-assignment rejection, and v1 backup compatibility. Gradle completed in 17 seconds with the existing ARM64 native library. APK versionCode is 218; debug signing, 16 KB ZIP alignment, ARM64 library packaging, and no-game-asset checks pass. Details are in `out/logs/android-b14-report.md`. No device is connected, so switching sets and exercising touch-through on a POCO remain unverified on hardware.

## b15 placement and sizing increment

b15 adds direct per-button size selection in the launcher editor, three dock arrangements (bottom-left, left edge, bottom-right) for the starting layout, and a draggable HIDE HUD / SHOW HUD button whose placement is included in the layout backup. Changing a dock or layout resets generated positions; the user can then drag individual buttons in-game. Backup format version 3 carries the dock setting and remains backward compatible with b13/b14 files. The underlying gesture mapper and engine are unchanged.

The b15 Android JVM suite passes 22 tests, including all dock/preset normalized coordinates and backup versions 1–3. Gradle completed in 17 seconds, reusing the same ARM64 Rust library. APK versionCode 219 passes debug signature, 16 KB ZIP alignment, ELF `0x4000` LOAD alignment and game-asset exclusion checks. See `out/logs/android-b15-report.md`. No device was available for visual/touch validation.

## b16 external keyboard and mouse increment

b16 adds three related input improvements while keeping the touch gesture path enabled in Touch and Mixed modes:

1. The Android winit backend now routes mouse cursor motion, five mouse buttons, and wheel events as mouse events. Previously the Android backend's TODO path treated mouse down/move input as touch.
2. Android virtual keyboard events (device ID `-1`, including the floating HUD's synthesized keys) are marked as synthetic so External mode can suppress HUD-injected input while accepting physical keyboards.
3. The launcher provides persisted Touch only, Touch + mouse + keyboard, and Mouse + keyboard only modes. The chosen mode is passed to the native game Activity before Rust input starts; Mixed remains the default.

The Android-only winit patch is kept in the vendored upstream `winit` 0.30.13 crate with its license and attribution files. No simulation, rendering, existing touch gesture mapping, or gameplay rules were changed.

For b16, the optimized ARM64 Cargo `fast` profile passed in 5m30s on the first Android target build. Gradle unit tests and APK packaging passed in 3m18s; 23 JVM tests and 2 focused Rust input-mode tests passed. APK versionCode is 220. Signature v2, package/version metadata, ARM64 library inclusion, 16 KB APK ZIP alignment, and ELF LOAD `p_align=0x4000` checks passed; the APK contains no game installation files. See `out/logs/android-b16-report.md` and the full build/verification logs in `out/logs/`. No Android device was attached, so mouse buttons, wheel, physical keyboard filtering, launcher mode persistence across process restart, and touch/HUD behavior remain unverified on hardware.

Build b2 compiled the ARM64 Rust library using the optimized `fast` profile and packaged a debug APK. Build b3 uses the optimized `shipping` Rust profile and produces independently branded Iron Harbor Mobile debug and release APKs (`org.ironharbor.mobile`). Build b4 adds full retained-log ZIP export and increments version code to 208. Eight Android JVM tests pass, including archive-completeness and cache-path checks. APK v2 signatures, 16 KB ZIP alignment, and `0x4000` ELF LOAD alignment pass. The package is ARM64-only and targets Android API 36. The export UI and Android content-provider handoff have not been exercised on hardware. See [the b4 log-export report](android-log-export-b4.md); detailed build logs remain local under `out/logs/`.

ADB currently sees no device. The launcher has not been installed or run on a POCO/Android 16 device, so its rendered appearance, SAF provider behavior, Vulkan initialization, audio, game launch/resume, touch response, floating HUD, and match behavior remain unverified on hardware. Continue/resume save detection, writable graphics/audio settings, a mod manager, live Vulkan information, frame telemetry, touch sensitivity, external-only control filtering, and Android text-entry remain unavailable or incomplete. No benchmark or FPS improvement is claimed.

The corresponding source is in the `android-development` branch. Preserve `LICENSE-GPL`, `LEGAL.md`, and upstream attribution when distributing it.

## b25 storage and Android helper crash fix

b25 makes three functional fixes based on the Android launch diagnostics, plus a build-code increment:

- SAF folders on primary shared storage resolve to a canonical filesystem path only when the user has granted all-files access. The resolver rejects other storage volumes, missing folders, and path traversal. Game import then validates the required archives and writes that real path to the engine config without copying the game folder.
- Play skips the game-folder mirror when the saved source resolves to the same canonical directory already configured in the engine. Other providers and storage setups retain the existing mirror path.
- If the user later revokes all-files access while the configured path is that shared game folder, Play stops with a recovery message instead of attempting to mirror the source onto itself.
- Android no longer tries to spawn the desktop `--map-picture` helper using `current_exe()`. On Android that resolves to `/system/bin/app_process64`, which caused the observed dalvik-cache ownership abort. Map-picture generation is unavailable on Android; this prevents the helper crash but may leave fallback map previews.
- Android `versionCode` increments to 229 (`0.3.6`).

The optimized ARM64 Cargo `fast` build succeeded in 6m54s. Ten Rust map-picture unit tests and four focused Android JVM test classes passed; the Gradle debug APK build succeeded. The APK is ARM64-only, targets API 36, is debug-signed, passes APK signature verification and 16 KB ZIP alignment, and its native ELF `LOAD` segments have `p_align=0x4000`. No game assets are bundled. APK: `out/apks/IronHarborMobile-v0.3.6-b25-arm64-debug.apk`.

No Android device was attached. Direct-path SAF behavior, launch latency on the POCO, mod startup, Vulkan/audio, and the absence of the helper abort still need hardware validation. There are no comparable before/after frame-time or launch-latency measurements, so b25 makes no performance-improvement claim. Without all-files access or for providers that do not map to primary shared storage, the game copy/mirror fallback remains and may still delay Play. Mods, Maps, and Saves are still synchronized from SAF on each Play.
