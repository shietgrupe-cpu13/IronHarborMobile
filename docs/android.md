# Android build

**Current incremental build: b23.** The launcher now leaves the selected game
folder in place and keeps only the private filesystem mirror required by the
Rust engine; saves, maps, mods and logs use `Documents/IronHarborRA2YR`. The
launcher can rotate into portrait and landscape, while the NativeActivity stays
landscape-first. JVM tests and ARM64 debug APK packaging passed. See the
[b23 storage and portrait report](android-storage-portrait-b23.md). Android
installation of b23 is pending because the device still has a b22 import in
progress; device-side SAF behavior and portrait rendering are not yet verified.

**B27 HUD and MO review:** C1–C10 now use dedicated Android control-group
events: tap assigns/recalls and long-press merges or clears according to the
selection state, without synthesizing desktop Ctrl+digit input. Set 1 and Set 2
selector buttons are included in the visibility editor, so either can be hidden
and restored. See the [Mental Omega compatibility notes](android-mo-compatibility.md)
for the actual dynamic roster, faction label, and loading-screen paths and the
limits of validation. A follow-up Android 16 MO skirmish reached `InGame` with
the MO Rules profile, and exposed missing mode INIs, effects, overlays, smudges,
and an AI team reference; see the captured findings in `out/logs`.

**Status: Android launcher baseline.** The contributor reported a debug APK playing
the intro movie, reaching the main menu, and importing a retail installation on an
Android 16 arm64 tablet (OnePlus Pad 2 Pro). Launcher baseline b2 builds and passes
JVM tests, but has not been exercised on Android hardware; the historical device
results below do not validate this branch. The open items at the bottom still
separate this from a release.

## Layout

Everything Android-specific lives in `android/`: it is both the
`vera20k-android` Cargo package and the Gradle project root.

| Path | What it is |
|---|---|
| `android/Cargo.toml`, `android/src/lib.rs` | The `vera20k-android` cdylib: `android_main`, the working-directory fix, and the hand-off to `vera20k::entry::run_with_loop`. Body is `#![cfg(target_os = "android")]`, so desktop builds compile it empty. |
| `android/app/` | The only Gradle module: `VeraActivity`, `hasCode="true"`. |
| `android/app/src/main/java/org/vera20k/VeraActivity.kt` | Starts the system folder picker and reads the selected tree through Android's `ContentResolver`. |
| `android/app/src/main/java/org/vera20k/LauncherActivity.kt` | Native Views command center for installation, settings, touch, mods, and diagnostics; starts `VeraActivity` only when required archives are present. |
| `android/app/src/main/java/org/vera20k/LauncherInstallation.kt` | Reads the configured game path and reports the two launcher readiness checks. |
| `android/app/src/main/java/org/vera20k/AssetImportJob.kt` | Owns import admission, cancellation, safe destination paths and atomic publication of the completed copy. Its filesystem tests run on the JVM. |
| `android/app/src/main/jniLibs/` | Where `cargo ndk -o` writes `libvera20k_android.so`. Generated, git-ignored. |
| `src/entry.rs` | Start-up shared with the desktop binary: logging, panic hook, banner, event loop, exit status. `main.rs` is now just the CLI switches plus a call into it. |

The library name must stay in sync: the crate is `vera20k-android`, so the
artifact is `libvera20k_android.so`, and the manifest's
`android.app.lib_name` is `vera20k_android`.

## Building

Prerequisites: `platforms;android-36`, `build-tools;36.1.0`, `ndk;28.2.13676358`,
`cargo-ndk`, a JDK 17+ that `JAVA_HOME` and `PATH` agree on. The Gradle wrapper
is committed, so no separate Gradle install is needed — the first `gradlew` run
downloads the distribution it pins.

Run every command below from the repository root.

```powershell
# 0. Once per checkout. AGP only auto-generates a debug keystore at
#    %USERPROFILE%\.android, and build.gradle.kts points it into the checkout
#    instead, so it has to be created here. android/.android/ is git-ignored.
New-Item -ItemType Directory -Force android\.android | Out-Null
keytool -genkeypair -keystore android\.android\debug.keystore `
  -storepass android -alias androiddebugkey -keypass android `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=Android Debug,O=Android,C=US"
rustup target add aarch64-linux-android

# 1. Native library for one ABI. Shipping strips symbols: an unstripped Rust
#    .so is commonly 100 MB+.
#    --platform 26 is required, not cosmetic: cpal's AAudio backend links
#    -laaudio, which only exists in the NDK sysroot from API 26, and
#    cargo-ndk otherwise links at its default level (21) and fails with
#    "ld.lld: error: unable to find library -laaudio".
$env:CARGO_PROFILE_SHIPPING_STRIP = "symbols"
cargo ndk -t arm64-v8a --platform 26 -o android\app\src\main\jniLibs build `
  --profile shipping -p vera20k-android --no-default-features --features phobos

# 2. Package. Point android\local.properties at the SDK first:
#    sdk.dir=D\:\\Programming\\android-sdk
android\gradlew.bat -p android :app:assembleDebug
```

`x86_64` is only for the emulator; install `x86_64-linux-android` with `rustup`
and add `-t x86_64` when you need it. Drop it for releases. On macOS/Linux, use
`mkdir -p android/.android`, forward-slash paths, a shell environment assignment
for `CARGO_PROFILE_SHIPPING_STRIP`, and
`./android/gradlew -p android :app:assembleDebug`.

## Android fork branding and launcher

The fork is branded **Iron Harbor Mobile**, with application ID `org.ironharbor.mobile`. The Android package uses an original shield-and-monogram vector icon and neutral dark splash; it does not package the upstream VERA icon, retail emblem, or scene splash. Both launcher aliases retained for the engine's icon preference show the Iron Harbor icon. `android/tools/make_launcher_icons.py` generates the adaptive icon resources. The engine namespace remains `org.vera20k` for its existing JNI entry points.

The Kotlin `LauncherActivity` uses platform Android Views and a landscape-first dark command-center layout. The launcher and existing NativeActivity run in separate Android processes; only the game process owns the winit event loop. Pressing Play starts the existing `VeraActivity` after checking for `ra2.mix` and `ra2md.mix` in the configured game directory. Import uses the system Storage Access Framework folder picker, copies to app-private staging storage, and publishes the new config only after a complete copy. It does not request all-files storage permission.

See [android-launcher.md](android-launcher.md) for launcher sections, supported controls, current limitations, and baseline verification.

## Mod menu music

Menu music plays automatically on the game's main menu through the selected mod's `[INTRO]` theme entry. If a selected mod has no playable `INTRO`, Iron Harbor uses the first playable `Normal=yes` theme from that mod's selected theme catalog. Vanilla Yuri's Revenge keeps its own `INTRO` behavior. Tracks must be WAV or AUD files reachable through the selected game/mod asset stack; BSM decoding is not supported. This is automatic at game startup, not a launcher music-picker screen.

## Release signing and ARM64 build b3

The b3 fork uses the independent product name **Iron Harbor Mobile** and application ID `org.ironharbor.mobile`; `org.vera20k` remains the JNI namespace for the existing native entry points. Its release signing key is machine-specific and must never be committed. Create `android/.android/android-release.jks` and supply `ANDROID_RELEASE_STORE_FILE`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, and `ANDROID_RELEASE_KEY_PASSWORD` to Gradle from a secure local secret store. The checkout ignores `android/.android/`.

For an ARM64 shipping build, set `CARGO_PROFILE_SHIPPING_STRIP=symbols`, build with `cargo ndk -t arm64-v8a --platform 26 -o android/app/src/main/jniLibs build --locked --profile shipping -p vera20k-android --no-default-features --features phobos`, then run `android/gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`. The explicit `phobos` feature enables MO/Phobos mod behavior; NPatch support is present in every build and switches on from NPatch-specific game data. Vanilla Yuri's Revenge keeps its existing behavior. Use JDK 17+, Android SDK 36/build-tools 36.1.0, NDK 28.2.13676358, and Gradle 8.13. The app declares only `arm64-v8a`, matching the native library produced by this recipe.

Build b3 verified six Android JVM tests, a shipping-profile ARM64 Rust build, signed debug and release APKs, the release application ID/SDK levels, 16 KB APK native-entry alignment, and 16 KB ELF LOAD alignment. The package contains no Yuri's Revenge installation files. No device was attached during this build; Android 16 POCO gameplay remains unverified. See [the b3 report](android-release-b3.md); detailed build logs are retained in the local `out/logs/` directory.

The b5 launcher increment adds installed-mod discovery, atomic engine-config selection for vanilla/mod and `yr`/`mo`/`auto` INI profiles, plus parser validation. Rust config tests (21), the existing MO archive-family test (1), and Android JVM tests (11) passed. Optimized ARM64, debug APK, release APK, signatures, APK/ELF 16 KB alignment, application ID, ABI, and no-game-assets checks passed. No ADB device was connected; runtime SAF, JNI selection, Android 16 launch, and gameplay remain unverified. See [the b5 report](android-mod-manager-b5.md).

The b6 increment adds SAF mod-folder import into private staged storage, atomic collision-safe publication and cleanup, plus progress/cancellation controls. Android JVM tests (14), debug/release packaging and signatures, APK/ELF 16 KB alignment, manifest metadata and no-game-assets checks passed. No Android device was connected; real SAF provider behavior and gameplay remain unverified. It does not infer profiles or claim full MO compatibility. See [the b6 report](android-mod-manager-b6.md).

The b7 increment refreshes the launcher design and generated original app icon, and clarifies how to access VERA20K's existing in-game layout editor and performance panel. Android JVM tests (14), debug packaging, and signed release packaging passed. Release lint analysis stalled and was excluded from the packaging retry. APK signatures, 16 KB alignment, manifest metadata, native library, and no-game-assets checks passed. No device was connected; RA2YRPORT's separate floating customizable action overlay is not yet implemented in VERA20K. No performance change or benchmark is claimed. See [the b7 report](android-launcher-ui-b7.md).

The b9 increment selectively adapts three `vera20k-MO` behaviors: Mental Omega's skirmish color ordering, Ares extended octagonal sight, and retirement of failed music tracks from automatic repeat selection. Focused Rust regressions (4) and optimized ARM64 shipping-profile compilation passed; Gradle packaging and JVM task completed successfully. The debug APK signature, ARM64 native library, APK/ELF 16 KB alignment, package/manifest metadata, and no-game-assets checks passed. No ADB device was connected, and no Mental Omega installation was available, so on-device MO behavior remains unverified. Phobos support is an opt-in engine capability and does not load the original Phobos DLL. See [the b9 MO compatibility report](android-mo-adaptation-b9.md); full MO compatibility is not claimed.

The b10 increment adapts Ares Find-or-Allocate behavior for `Weapon.Anim` and `Warhead.AnimList`, and applies Phobos `SW.ShowCameo=no` to right-hand build sidebar candidates while leaving the vanilla build path unchanged. Focused regressions (3) passed. The optimized ARM64 shipping build completed in 31m44s; Gradle packaging completed in 1m37s, with JVM tests up-to-date. APK signature, metadata, ARM64 library, ZIP and ELF 16 KB alignment, and no-game-assets checks passed. No device was connected; runtime Mental Omega behavior remains unverified. Full MO compatibility is not claimed. The local build report and logs are in `out/logs/android-mo-adaptation-b10-report.md` and `out/logs/android-mo-adaptation-b10-*`.

## Full Android log export — build b4

The Diagnostics screen can export a ZIP containing the complete retained regular files in app-private `files/logs/`, including the engine's current and rotated files, plus the panic report `files/except.txt` when present. It includes the device summary and an inventory of included files. A private, non-exported content provider grants temporary read access only to generated ZIPs in the app cache; no storage permission is added. System logcat is not included. The b4 release APK increments version code to 208 so it can update the b3 package when signed with the same local release key.

The two new archive tests verify byte-complete current/rotated logs and panic reports, exclusion of unrelated app files, and rejection of traversal/missing export paths. The six existing Android JVM tests also pass. The current device has no ADB connection, so the Android Sharesheet/receiving-app handoff has not been tested on-device. See the [b4 log-export report](android-log-export-b4.md); detailed build logs remain in the local `out/logs/` directory.
## LAN play on Android

LAN play between devices needs all of the following, and a missing one looks
exactly like a discovery bug:

- both peers on one subnet and one access point, with the access point's client
  isolation off;
- both on the same band when the router gives 2.4 and 5 GHz different subnets;
- a desktop peer's firewall allowing UDP 1234 inbound.

The LAN lobby binds the app to Wi-Fi while it is open
(`ConnectivityManager.bindProcessToNetwork`, `util::lan_network`), so its
broadcasts leave by Wi-Fi even when cellular is up or the Wi-Fi has no
internet. A Wi-Fi change while the lobby list shows reopens the lobby's sockets
on the new network; a change during a room or a match waits for the lobby.

Two more conditions come from the game:

- **One build, one feature set** ([build check](Networking.md#build-check)). The
  APK is built with `--no-default-features`, so a desktop peer must be too. The
  host's game shows in the list, but the host refuses the join: the guest sees
  Request denied and "VERA20k build or enabled gameplay features differ."
- **Player names are the LAN identity**, and a clash looks like "the other
  player does not exist". A player discards advertisements whose name matches
  its own, so two peers left at the default `[New Player]` never see each
  other's games. Android has no text input yet, so the activity
  appends the last three hex digits of the Android ID: a fresh install enters
  the lobby as `[New Player]-1A2` (`util::device_identity`). The ID survives
  reinstall and an app-data clear and changes only on a factory reset. Every
  other platform keeps the stock name.

A guest also needs the host's map in its own map library.

## Deliberate choices

- **`NativeActivity`, not `GameActivity`.** Smallest M0. It has no IME, so text
  entry (player name, chat, passwords) is unusable until P6 chooses: switch winit
  to `android-game-activity`, or overlay a Java `EditText` and add JNI.
- **Vulkan default; OpenGL ES experimental.** wgpu's Android build includes
  both Vulkan and GLES. The launcher stores `[VERA] GraphicsAPI` in the normal
  `VERA.INI` profile and applies the selected backend on the next game start.
  Vulkan is optional at install time; OpenGL ES 3 is the package's minimum
  graphics capability. The existing GL compatibility renderer uses reduced
  feature limits and has not yet been validated on physical Android devices.
  If a selected backend cannot initialize, startup reports the failure and
  retries the engine's default backend.
- **`configChanges` covers everything winit handles** so rotation and
  split-screen arrive as a resize, not an activity rebuild. A second activity in
  the same process cannot work — winit builds one event loop per process — so
  `android_main` ends the process rather than leaving the new activity on its
  splash.
- **System folder picker.** The importer requests read access to the selected
  tree and writes only to app-private storage. It needs no all-files permission.
- **Import before switching installations.** Each copy goes into a new
  app-private directory. Only a successful copy updates the game path through
  the Rust configuration owner, preserving other settings and the previous
  installation. Cancelling waits for the worker to stop and clean up before
  another attempt can start.

Run the import regression tests with `./android/gradlew -p android :app:testDebugUnitTest`
(`android\\gradlew.bat -p android :app:testDebugUnitTest` on Windows).

## What is still open

1. **Release validation.** The recipe uses `--profile shipping` and strips
   symbols, but the contributor's earlier device results were from a debug APK.
   Rebuild the current code, inspect ELF/APK alignment and signing, and repeat
   device testing before treating it as a release.
2. **Live import progress.** The dialog shows the copy's step and the folder it
   landed in, and the copy refuses a folder without `ra2md.mix`/`ra2.mix` before
   touching anything, but the byte counter is wired and unexercised: the copy
   reports every 16 MB and the card renders `Copying: N MB` only if a frame draws
   between two reports, which a fast local copy can skip entirely.
3. **Portrait, not landscape.** The manifest asks for `sensorLandscape`, but
   Android 16 ignores manifest orientation on large screens: the surface came up
   2400x3392 in portrait (log below). Rotation and a portrait-capable layout are
   P4/P6 work, not a manifest fix.
4. **No on-device error surface.** `system_dialog` has no Android branch, so a
   fatal start-up error here is only visible in `files/logs/ra2.log`. A
   `Toast`/`AlertDialog` via JNI is still open.
5. **One process, one game.** Exiting from the menu ends the process on purpose:
   winit cannot build a second event loop, so a cached process would serve the
   launcher's next tap nothing but a splash. A future multi-activity design would
   have to rework the entry around that constraint.

## Contributor-reported results before review fixes

These are the original contributor's results, retained as historical context.
They have not been independently reproduced for the current code.

- `cargo check -p vera20k -p vera20k-core -p vera20k-android` (host): passes.
- `cargo ndk -t arm64-v8a --platform 26 ... build` links `libvera20k_android.so`:
  ELF64 AArch64, `android_main` exported, all LOAD segments 0x4000-aligned, so
  NDK r28 already satisfies Android 15's 16 KB pages.
- `gradlew :app:assembleDebug` produces an installable APK:
  `org.vera20k`, versionCode 207, versionName 0.2.7 (read from `Cargo.toml`),
  minSdk 26, targetSdk 36, native-code `arm64-v8a`. `zipalign -c -P 16` reports
  the packaged `.so` at 16384-byte alignment and `apksigner verify` passes.
- **On hardware** (OnePlus/OPPO OPD2413, Android 16, API 36, Adreno 830):
  `adb install` + launch reaches `Initialization complete` with
  `Vulkan` adapter `Adreno (TM) 830`, surface `3392x2400 Rgba8UnormSrgb`,
  AAudio at 512 frames/44100 Hz, and the log at
  `/data/user/0/org.vera20k/files/logs/ra2.log` — so the data-directory,
  settings, logging, Vulkan and audio paths all work on Android.
- **The intro movie plays and touch skips it.** Tapping during the movie logs
  `Touch skipped the movie` and lands on the shell (`Loaded ra2ts_l.bik for main
  menu`, music `Drok`), which paints.
- **The window carries `KEEP_SCREEN_ON`** for the process lifetime, so the movie
  holds the display without synthetic input.
- **Leaving and returning works both ways**: screen-off then wake, and HOME then
  re-entry, both log `Application resumed — surface rebuilt` and draw the menu
  again; exiting from the menu then tapping the icon is a cold start.
- **A successful import was exercised.** With no installation the card offers
  Import Game Files; Choose Folder opens the system picker, the chosen tree is
  copied into `files/ra2`, `files/config.toml` is written, the card names that
  copy (`Game files: /data/user/0/org.vera20k/files/ra2`), and the next launch
  plays. A folder without `ra2md.mix`/`ra2.mix` was refused before copying. Failed
  and cancelled copies need device testing with the updated import flow.
- Build the native side with `--profile fast` (or `shipping`) for anything you
  run on the device: the `dev` profile is slow enough that the intro movie
  starves the main thread and Android kills the app for an ANR.
- Toolchain used: cmdline-tools 23.0, platforms/android-36, build-tools 36.1.0,
  NDK 28.2.13676358, AGP 8.11.1, Gradle 8.13, JDK 19.

## Windows local-build gotchas

- Run Cargo from a Developer PowerShell. Without `INCLUDE`/`LIB`, `ring`'s C
  build dies with `D8050: cannot execute 'c1.dll'`.
- Point `TMP`/`TEMP` at a directory inside the checkout. The Android Clang
  driver otherwise fails `unable to make temporary file: Permission denied`
  while `ring` builds its assembly in parallel.
- Point `GRADLE_USER_HOME` inside the checkout when `%USERPROFILE%\.gradle` is
  not writable: Gradle otherwise fails with
  `Could not initialize native services … Failed to load native library
  'native-platform.dll'`.

## B22 Mental Omega compatibility work

B22 selectively adapts three findings from the `928274/vera20k-MO` branch while
keeping this fork's existing launcher and vanilla engine. Its Android ARM64
recipe enables the existing opt-in `phobos` feature. The cold-start rules path
now reads the selected root `[ArmorTypes]` catalog before binding
`TechnoType`/`Armor=` values, matching the Ares initialization order. Screen
presentation also keeps a cloak-capable enemy as the sensor ghost during MO's
single-stage state-zero frames; forced sensor and map-editor queries retain
their existing behavior.

Regression checks passed for custom armor cold-start binding, the MO sensor
ghost state-zero case, and the existing vanilla cloak transition. B22 was built
for Android ARM64 with the optimized `fast` profile and Phobos enabled; package
metadata is app ID `org.ironharbor.mobile`, version 0.3.6 / code 226, min API 26,
target API 36. The current device run remains unverified: no Android device was
attached. These changes do not establish full Mental Omega compatibility;
mod-specific DLL behavior, rosters, effects, archives, and match scenarios still
require tests with the user's own files on an Android device.

## TODO

Save/Map files import/export, touch sensitivity/overlay configuration, audio
focus, LAN locks, CI.

## B27 — shared game storage, nested mod discovery, and preflight diagnostics

The game folder picker no longer forces creation of the separate Documents data folder. The selected retail game directory remains the game source; `Documents/IronHarborRA2YR` is for user-managed Mods, Maps, Saves and Logs. SAF-only game sources still require a private filesystem mirror because the Rust engine consumes filesystem paths.

The launcher scans the selected Documents Mods tree on a background thread, finds nested mod folders up to four levels deep, and presents them by relative path. It mirrors and validates only the selected mod for the engine instead of synchronizing the entire Mods tree on every Play. Existing private-only mod folders remain supported. User-managed originals in Documents are not deleted by this process.

Play records durations for game-source sync, selected-mod sync, Ares, Maps, Saves, and engine selection validation in `launcher-preflight-latest.txt`; the full diagnostics export includes that trace. This diagnoses launch/file delays. When the in-game Performance Stats panel is visible, the engine also logs one numeric sample per second with FPS, frame time, simulation TPS, unit count, GPU time, CPU areas, core/thread loads, and event-loop rates. Compare captures under equivalent workloads before attributing a performance change; the measurements do not imply an FPS improvement by themselves.

Launcher Settings now exposes Camera & Zoom minimum and maximum bounds, defaulting to `0.75` and `2.0` within the engine's existing `0.25`–`4.0` camera scale. The values use `[graphics] camera_zoom_min` and `camera_zoom_max` in the engine's `config.toml` parser/writer. Pinch, replay wheel zoom, and the optional in-match wheel view-distance steps obey the configured bounds; an Android service forwards updates into the existing `:game` event loop while it is alive. Otherwise the saved range is read at next launch. Windows uses the same engine configuration and clamp paths; the launcher sliders are Android UI. There is no standalone camera zoom keyboard binding in the current engine, so no new shortcut was added.

Launcher Settings exposes a 50–100% world render scale in 5% steps as an **experimental beta**; 100% is the default and recommended value. Lower settings have been reported to offset the movement indicator and omit building textures, so they are not recommended for normal play until those rendering issues are corrected. The `render_scale_percent` and `fps_limit` keys live in the existing `[graphics]` section. A non-native world target is used for battlefield rendering and upscaled into the existing full-size frame; screen-space UI remains at the surface resolution. The chosen scale takes effect on the next game launch. The FPS cap gates presentation only; simulation and multiplayer pacing are unchanged. The engine's default/display pacing can limit actual output below 120 FPS. Adaptive resolution is not implemented. Lowering scale may reduce GPU pixel work, but does not reduce CPU-side world-instance preparation; no performance gain has been established.


## B31 ? direct shared storage and single user-data root

The Android launcher uses `Documents/RA2Harbor` as the engine data directory.
`Config/config.toml` is the Android config location; engine settings remain in
`Config`, while maps, mods, saves, and logs use `Maps`, `Mods`, `Saves`, and
`Logs`. The engine's Android startup reads a small app-private path pointer and
sets its working directory to the selected shared folder. Game archives remain
at the folder selected in SAF and are read from that original location. Game
selection and mod selection no longer stage a second engine copy. Mods are
selected in place from `Documents/RA2Harbor/Mods`, including supported nested
folders. Map imports are published once into `Maps`.

The app reuses an existing `IronHarborRA2YR` or `RA2IRONHARBOR` folder by
renaming one to `RA2Harbor` when possible; it does not delete legacy folders or
overwrite conflicting files. It migrates missing app-private settings and
user data into the selected root once, retaining the source files. If an older
shared folder cannot be renamed, it remains untouched and the launcher reports
it. Because the Rust engine consumes normal filesystem paths, Android 11+
All files access is required for direct shared-storage game/mod reads and for
using the shared folder as the engine working directory. SAF-only/cloud
providers that cannot map to a local primary-storage path are rejected without
copying their game or mod contents. This permission remains declared and is
user-granted in Android Settings.

Build B31 verification results and device-test status are recorded in
`out/logs/changelog-b31.txt` after the build completes.
