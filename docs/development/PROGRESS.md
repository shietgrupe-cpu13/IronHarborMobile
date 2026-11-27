# Iron Harbor Mobile progress

Updated 2026-11-28. Current authoritative plan: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## 2026-11-28 — Red Resurrection catalog and HUD layout persistence

- Fast-forwarded `android-new` from `c73a8606f` to remote `ae1ce7c09`, preserving untracked `out/`. The six incoming commits are upstream engine changes; no local build was run.
- Device logs pulled to `E:\Temp\IHLogs-20261128` identify Red Resurrection as the selected mod. Its `Resources/ClientDefinitions.ini` selects `INI\\MPMaps.ini`; that catalog has `[MultiMaps]`, map-level `GameModes=...`, and named mode sections but no `[GameModes]` registry. The current `ClientInstall::read` therefore builds an empty client mode/map set; startup then warns that `MPModesMD.ini` has no usable rows. This is a source-backed explanation for the empty in-game map catalog, not yet a device-verified fix.
- Added a guarded legacy-catalog fallback in `src/assets/client_install.rs`: infer mode candidates only when the registry is empty, only from declared map memberships/aliases with a corresponding section, then retain existing mode/resource and unsupported-option validation. Added a synthetic regression covering aliases, nested map path, and an unsupported mode being excluded.
- HUD launcher backup/import already used a validated versioned format (v1–v3), while the current compact in-game overlay stored positions separately under `touch_hud/button_positions_v1`. The overlay now reads/writes the launcher preference store and migrates old compact IDs/positions non-destructively. Export/import also retains active set and hidden state; older files still decode with safe defaults. JVM regressions cover migration and old/new backup round-trips.
- Added the new Rust regression to `.github/workflows/sync-upstream-android.yml`. `git diff --check` passed. Kotlin/JVM, Rust tests, ARM64 APK build, and device behavior remain unverified until the remote Actions run and POCO retest.
- First run [46041308051](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46041308051) failed in `:app:compileDebugUnitTestKotlin`: the new backward-compatibility assertion referenced `assertFalse` without importing it. Production Kotlin compiled; Rust/ARM64/APK steps were skipped. Fixed the missing import; rerun required.

## Current renderer and Mental Omega regression work

- Device feedback says removing an old `config.toml` fixed game-directory selection; the exact path/content was not supplied. Preserve non-destructive configuration precedence.
- Commit `db2917dd5` is pushed with Play preflight de-duplication, asynchronous launcher refresh/mod/profile selection, MO identity-based profile selection and nested-map discovery, UI-thread picker dispatch, JNI/lifecycle changes, timing reports and focused regression tests. Kotlin compilation/JVM tests passed in Actions, but the map regression failed before native ARM64 compilation, so no APK was produced.
- Follow-up commit `ac026b031` improves the shared map-path resolver to match directory components case-insensitively. Run [46027519203](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46027519203) confirmed it opened the nested file, then exposed the invalid fixture. The earlier successful run [46010200544](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46010200544) predates this work and is not evidence for these fixes.
- The debug build version code is 208 (previous device artifact: 207) to permit an update, subject to signing-certificate compatibility. CI and physical-device validation are required; do not call these regressions fixed yet.
- Actions run [46023643484](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46023643484) compiled the Kotlin/JVM stage and reached Rust tests, then failed because the nested map’s directories differed in case (`MapsMO/Standard` vs `mapsmo/standard`). Run [46027519203](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46027519203) then confirmed the case-insensitive resolver opened that file, but failed parsing the synthetic fixture because it omitted required `[IsoMapPack5]` terrain data. No ARM64 build/APK was produced. The fixture now uses an existing valid one-cell map payload and needs another Actions run.

## Latest device regression and current change

- Upstream commit `814b6c3c0` is merged at `ac39fc2db` and included in pushed commit `6afcd6798`. Commit `ebc52b3fd` removes launcher campaign navigation and migrates Android maps; run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) completed successfully. Its ARM64 debug artifact is [available here](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660/artifacts/15194923757). The newer mod-diagnostics commit is under validation in run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713).
- The supplied Android 16 Xiaomi diagnostic identifies two separate events. First, pid 6505 received an explicit `SIGKILL` at `VeraActivity.applyAppIconOnUi(VeraActivity.kt:900)` during game startup. The cause is confirmed: startup icon selection flips launcher aliases and deliberately kills the process. The secondary alias and Android VERA-icon controls/callback were removed in `4933e78d0`; run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed package checks, but device retest remains pending.
- A later game process (pid 8754) has a distinct shutdown abort: AAudio streams close, then CheckJNI reports `Thread-5` calling `DeleteGlobalRef` with the main thread's `JNIEnv*`; Android also reports destroyed-mutex waits in HWUI's `CommonPool`. The supplied tombstone backtrace is only the HWUI worker, not the offending Thread-5 stack, so the exact owner is not established. Do not mark this crash fixed; trace the CPAL/JNI ownership and Android shutdown path before changing it.
- The same diagnostic shows the launcher preflight succeeded in 66 ms and the engine loaded configured vanilla YR roots from `/storage/emulated/0/Documents/RA2Harbor/Config/config.toml`. This does not verify the listed mod/profile/map/renderer requirements.

## Repository state

- Branch: `android-new`, tracking `vera20k-fork/android-new`.
- Current HEAD: `106c5184c`, pushed to `vera20k-fork/android-new`; run `46007825583` failed before ARM64 compilation because the final MO test filter matched zero tests. A corrected filter is staged locally.
- Legacy source reference: `android-development` at `1293fb441727e2522f6a284d88faf4f108a1ee3e`; its backup refs remain preserved. Do not merge the branch wholesale.
- `out/` is untracked user/project data and has not been read, changed or deleted.
- Launcher/storage/engine integration and package verification are committed and pushed. The workflow's tested upstream merge is now fast-forwarded locally; `out/` remains untracked and untouched.

## Completed and verified previously

- HUD key dispatch no longer calls inaccessible `KeyEvent.recycle()` (`2e19b4453`); the fix and API compatibility test passed in GitHub Actions.
- Android JVM file helpers were corrected for Android API compatibility (`d3f9e61be`).
- ARM64 debug APK artifact upload was added (`529cbe103`).
- GitHub Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) passed Kotlin/JVM validation, ARM64 Rust `fast` build, debug APK packaging and artifact upload. It produced a 53,330,817-byte artifact with 14-day retention. This is an older artifact and does not contain the current local storage changes.
- Master plan added remotely at `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e`; local branch was fast-forwarded without losing source changes.

## Current implementation and validation

- `AndroidSharedStoragePath.kt` restores `sharedDataRoot`, reuses established roots, and resolves supported primary-volume SAF IDs to paths. Direct game selection remains read-in-place.
- Game validation is profile-aware: RA2 requires `ra2.mix`; YR requires `ra2.mix` and `ra2md.mix`. Explicit base-RA2 detection is used when importing a new installation, and Rust updates the engine profile through its config writer.
- Launcher and engine configuration lookup now prefers `Config/config.toml`, then the data-root legacy `config.toml`; publication writes the canonical file without replacing the legacy file. Private-data migration no longer shadows an existing root-level config or `VERA.INI` by creating a canonical copy.
- Legacy JNI/storage integration was inspected on both Kotlin and Rust sides. Launcher JNI calls have matching Rust exports; the folder-picker/import JNI methods are registered against `VeraActivity` and its signatures match. The current game HUD routes keys through GameActivity's normal key callbacks rather than separate JNI.
- The old Ares support path was traced in `android-development`. Current Android startup, launcher preflight and asset-manager reconstruction now pass `Mods/AresSupport`; only `ares.mix` is considered, only for a selected mod, beneath that mod's own copy. Vanilla does not see the shared archive. A focused test covers vanilla isolation and mod precedence.
- The existing NativeActivity SAF copy importer remains in place. The new launcher direct-picker flow still cannot import a cloud/provider tree as a fallback; it reports that a local folder is required. Do not describe launcher SAF fallback as working until wired to the importer.
- Actions run [45981409504](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45981409504) passed Kotlin compilation/JVM tests, focused Rust config/mod/Ares regressions, ARM64 native compilation, Gradle APK assembly, package identity, debug signature, ZIP alignment, ARM64 library presence, ELF 16 KB LOAD alignment, and artifact upload. The APK package is `org.ironharbor.mobile.debug`. Artifact [download](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45981409504/artifacts/15189579979), named `IronHarborMobile-android-new-arm64-67f6bc1daf329113e4a01d4ae06267f179b57b6f`, is 55,151,674 bytes and retained through 2026-12-11. The built tree contains fork commit `67f6bc1da` plus upstream commit `cf0b6613e`; CI then pushed merge commit `ace04413e`. No device test was performed.

## Known gaps and gates

- Physical-device behavior is not verified; the successful CI APK is a debug artifact only.
- Launcher SAF copy fallback for cloud/non-path providers is not connected. The legacy GameActivity importer remains, but the main launcher cannot yet route a new user into it without starting gameplay.
- A stale `CameraZoomSettingsService.nativeApplyCameraZoomLimits` declaration has no Rust export in the current branch, is not referenced by the launcher and is not registered in the manifest. It remains dormant; do not expose this as a working setting until its engine integration is migrated and validated.
- Private app data migration is non-destructive and copies only missing entries, but its behavior needs JVM coverage; no old source is removed. Config and VERA.INI migration now skips copying whenever either canonical or root-level shared files already exist.
- Engine source supports `GraphicsAPI=opengl` through wgpu's GL backend; Android feature inclusion and device behavior are not verified, so no Android launcher renderer selector is exposed yet.
- Persistent release signing, full APK metadata/ABI/alignment checks, nightly release flow and the internal sync PR review remain open.
- Physical gameplay validation is incomplete. User report says game archives failed with `EACCES` and no game installation configured; exact device/API/artifact and failing path were not captured.

Committed milestone `ebc52b3fd`: removed the launcher Campaigns page/Home shortcut and dead campaign-selection intent; Android map paths now target `Configs/Maps`, while non-Android keeps `Maps`. Added non-destructive migration of legacy root Maps into one canonical folder, Rust path coverage, Kotlin migration coverage, and a focused Actions Rust test. Static review and `git diff --check` passed; no local compilation or tests were run. Hosted validation [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed. No device verification yet.

The selection trace confirms Android startup adopts the persisted data root, `GameConfig::load` reads the same published config, and `Mods::select_resources` calls the same `ModDefinition` / `asset_manager::mod_resources` resolver used by launcher validation. This source-level parity is not physical-device proof; the next device test should compare the preflight's effective resource report against the mod actually shown in game.

## Current diagnostics change (`39a49777f`, pushed in `6afcd6798`)

The source trace confirmed launcher selection and engine startup both resolve a selected folder through the same `ModDefinition` / `asset_manager::mod_resources` path; Android startup then loads the selected shared config root before `App::initialize` uses `Mods::select_resources`. The old preflight discarded the Rust validator's return value and only listed three INI roots. Commit `39a49777f` captures the result and reports the config path, canonical mod path, effective profile and INI names, registered startup archive names, and mounted `ares.mix` source. It also removes duplicate mod-manifest validation/config publication after folder selection and the TOML-only resource summary that could misstate manifest-selected files. This improves evidence for mod-switch reports; it does not prove on-device MO compatibility or campaign gameplay. Remote Kotlin/Rust/APK validation is pending in run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713).

Next: inspect run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) and verify its APK artifact. Then use the new preflight report in a device test to investigate actual mod/MO resource selection. Continue with the explicit launcher SAF fallback, renderer feasibility and shutdown ownership. Preserve upstream sync. No local Rust, Cargo, Gradle, or APK build/test.
