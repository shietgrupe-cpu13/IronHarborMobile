# Iron Harbor Mobile b5 — Mod Selection and Profile Baseline

- Build date: 2026-11-18
- Source branch: `android-development`
- Application ID: `org.ironharbor.mobile`
- Version: `0.3.6` (`versionCode` 209)

## Three focused changes

1. **Launcher mod inventory.** The Mods page reads immediate subfolders under the engine's app-private `Mods/` directory and reads the persisted mod/profile choice from `config.toml`. It distinguishes a missing selected folder from an installed choice and refreshes the state on launcher resume.
2. **Engine-backed mod selection.** Vanilla Yuri's Revenge or an installed data-mod folder can be selected in the launcher. Rust's existing config writer updates `[paths] mod`; the launcher validates the complete result with the engine config parser, writes it to a temporary file, syncs it, and atomically replaces `config.toml`. Empty `mod` selects vanilla. The `:game` process reads this choice on its next start, so an active match is not mutated.
3. **Engine-backed INI profiles.** The launcher exposes the existing `yr`, `mo`, and `auto` profiles. The Rust writer updates `[ini] profile`; switching from `custom` to a preset comments out custom `rules`, `art`, and `ai` overrides so the engine does not reject a mixed preset/custom config. The launcher labels these selections as next-launch settings.

## Mental Omega fork review

Reviewed [`928274/vera20k-MO`, branch `mo`](https://github.com/928274/vera20k-MO/tree/mo), tip `8fdf13cf03a50848a5aa220601d2cfb238a462fe`, and its [MO adaptation notes](https://github.com/928274/vera20k-MO/blob/mo/docs/MO-ADAPTATION.md).

The current VERA20K checkout already has `IniProfile::Mo`, `ArchiveFamily::Mo`, MO root names, and a fixture for `expandmo##`, `mapsmo##`, `multimo`, and `movmo##`. That existing engine path was not copied from the MO fork in this build. Its fixture passed. This launcher increment only exposes the existing profile and mod-selection inputs.

The MO fork also documents deeper compatibility changes, including tolerant missing-root handling, MO-specific skirmish colors and sight limits, and Ares/Phobos gameplay and rendering behavior. Those are engine changes, not launcher wiring. The Android shipping build still uses the existing `--no-default-features` configuration, so this APK does **not** claim the MO fork's full compatibility. No broad branch merge or gameplay patch was made. No MO retail assets were available for a launch test.

The existing `auto` profile only detects a complete INI root set after startup archives are mounted; the engine keeps the Yuri's Revenge archive family for `auto`. The launcher explains this and requires the player to choose `mo` when the installation/mod uses MO-named archives. Automatic profile inference and archive-level validation remain follow-up work.

## Verification

- Rust engine config tests: **21 passed**, including mod selection, vanilla reset, preset profile persistence, and engine parser validation.
- Existing engine MO archive-family regression: **1 passed** (`mo_family_mounts_spawner_renamed_archives_and_their_ini_roots`).
- Android JVM tests: **11 passed** across `AssetImportJobTest`, `DiagnosticLogArchiveTest`, `LauncherInstallationTest`, and `LauncherModCatalogTest`.
- Optimized Rust ARM64 shipping build: passed for `aarch64-linux-android`; `libvera20k_android.so` is 67,492,560 bytes.
- Gradle `testDebugUnitTest`, `assembleDebug`, and `assembleRelease`: passed.
- Debug and release APK signatures: passed with APK Signature Scheme v2. Release certificate SHA-256: `7440ad6a732f410f570b55b8d7e0138b0a9ab318ce5553afc02e8e0527ce1711`.
- Both APKs passed `zipalign -c -P 16 -v 4`. Every packaged ELF `LOAD` segment has `0x4000` alignment.
- APK metadata: application ID `org.ironharbor.mobile`, `minSdk 26`, `targetSdk 36`, ABI `arm64-v8a`, `versionCode 209`. The exported `nativeConfigForSelection` symbol is present in the packaged shared library.
- The APK contains no MIX/MAP game asset entries. No gameplay or performance optimization was made; no performance claim is made.
- `adb devices -l` reported no connected device. SAF interaction, Android 16 launch, live JNI selection, Vulkan startup, and actual game/mod asset loading remain unverified on hardware.

## Artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `IronHarborMobile-0.3.6-android-arm64-debug-b5.apk` | 68,383,576 bytes | `C37633CFCE0592692FFE248E7B13A18C6AD361D055353A57E293FECAF16DE0EB` |
| `IronHarborMobile-0.3.6-android-arm64-release-b5.apk` | 68,170,637 bytes | `ECC59592C096386C709E59BD014ED2DD055DE5572FDCAB728672ED41D6F81185` |

Both files are in `out/apks/` and mirrored to `E:\Port Apks\VERA20K-ANDROID\`.

Build and verification logs are in the local `out/logs/` directory, including:

- `android-mod-manager-b5-rust-tests-final.log`
- `android-mod-manager-b5-mo-assets-test.log`
- `android-mod-manager-b5-gradle-and-apk.log`
- `android-mod-manager-b5-arm64-shipping.log`
- `android-mod-manager-b5-apk-verification.log`

## Known limitations and next build

- This build lists and selects already-installed mods; it does not import, remove, or validate SAF-selected mod folders.
- It does not infer a mod's profile from its archives or inspect a complete installation through the engine's runtime asset loader.
- Changes to a mod or profile apply on the next game-process launch. The engine has no Android live resource-reload interface; switching assets inside an active match would be unsafe.
- Settings beyond the exposed INI root profile are not changed here. No performance instrumentation or benchmarking was run.
- Android device behavior still needs validation on an Android 16 ARM64 phone, including the requested POCO.

The next increment should implement SAF mod import with staged, collision-safe publication and use engine-owned asset/profile validation before offering a profile as ready. It should not describe Mental Omega as fully compatible until the missing MO engine adaptations have been tested.
