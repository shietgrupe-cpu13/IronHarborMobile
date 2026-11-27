# Iron Harbor Mobile handoff

Updated 2026-11-28. Read [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md) as the authoritative specification.

## Active checkpoint — map catalog and HUD preference integration

- Branch `android-new` fast-forwarded to `ae1ce7c09` from the remote, preserving untracked `out/`. Current source changes are uncommitted; do not stage `out/`.
- Device logs and the selected Red Resurrection files are in `E:\Temp\IHLogs-20261128`. `ClientDefinitions.ini` selects the mod's `INI\\MPMaps.ini`; it contains map-level mode memberships but no `[GameModes]` registry. `ClientInstall::read` currently yields no modes, then skirmish roster filtering drops every mode. A conservative inference fallback and focused Rust regression are now present.
- `TouchHudSettings` already owns import/export (format `iron-harbor-hud`, versions 1–3). The in-game compact overlay previously used another SharedPreferences file and format. It now uses the launcher store, migrates legacy coordinates without deleting them, and applies launcher IDs/coordinates. Export/import now also preserves active control set and hidden state while accepting old files. Kotlin regressions were edited but not yet run.
- The only validation run so far is `git diff --check`; no local Rust, Gradle, or APK compilation was performed. The workflow now includes the new Rust test. Next: review/fix the diff, update/checkpoint docs, commit and push `android-new`, monitor GitHub Actions logs, fix deterministic failures, then install its debug APK for map and HUD-layout device checks. Do not claim runtime verification until tested on device.
- Stable signing was added in the preceding workflow change. A normal update with the same package/signature preserves app preferences; a prior uninstall/reinstall can erase private preferences. Shared `Documents/RA2Harbor` data is separate and must remain untouched.

## Active checkpoint — launcher stalls, MO and shutdown

- Current checkout: `E:\VERA20K-ANDROID`, branch `android-new`, pushed HEAD `db2917dd578b3b3727346d5e61e8131df04ffd5c`; one follow-up source/doc change is uncommitted. Untracked `out/` is user data and must remain untouched.
- Commit `db2917dd5` failed its nested map test because `local_map_path_in` could not resolve directory components with different casing. Follow-up `ac026b031` changed the shared resolver; run [46027519203](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46027519203) then opened the map but failed because the test fixture omitted required `[IsoMapPack5]` data. The fixture now uses an existing valid one-cell map payload; no APK has been produced.
- Root-cause evidence: the 26,312 ms Play preflight constructed an `AssetManager`, indexing selected archives before engine startup repeated that work. Current changes defer archive indexing to engine startup, move launcher refresh/mod/profile selection off the UI thread, record operation timings, add a cancel affordance and skip identical config writes.
- MO changes infer profile from manifest Id/Name and discover the loose map catalog by `[MultiMaps]` contents, validating nested relative paths before loading. Focused tests and workflow commands were added. MO mode/roster behavior and actual loaded resources remain unverified.
- JNI/lifecycle changes avoid owning the loader's Activity global reference, clear the retained AndroidApp on normal native return, delay process retirement, and dispatch folder dialogs/pickers to Android's UI thread. Kotlin/JVM CI passed; Rust regression failed before ARM64/APK validation. No device validation has run; do not report the shutdown crash fixed.
- The nested-path resolver fix passed its regression's file lookup in run #24; only the test fixture was invalid. The updated fixture is uncommitted and must pass the same production loader regression in the next Actions run before ARM64/APK validation. Then install and test on the POCO with equivalent game/mod/profile. Do not compile Rust, Gradle or APK locally.

## 2026-11-27 renderer/MO checkpoint

- Latest user feedback: deleting an old `config.toml` corrected the game folder. Exact path/content was not supplied. Android config search currently prefers shared `Config/config.toml`, then shared root `config.toml`, then prior app-private entries. Preserve this non-destructive fallback until a test captures both competing files and their selected paths.
- Commit `106c5184c` pushed to `android-new` restores Automatic/Vulkan/OpenGL ES in launcher Settings, JNI persistence through engine-owned `GraphicsAPI` in VERA.INI, one alternate Android backend attempt, and explicit MO profile/resource/archive resolution. Device renderer and MO gameplay remain unverified.
- Actions run `46007825583` passed launcher/JVM checks and preceding focused Rust checks. It failed at the final MO asset-manager test because its filter named `assets::asset_manager::install_inspection_tests::...`, while the test is in inline module `assets::asset_manager::tests::...`; zero tests matched. No ARM64 build or APK was produced.
- The workflow filter is corrected in the current local change. Keep the no-local-compilation rule; commit and push this workflow fix, then inspect the new run through artifact upload. No release is authorized by this task.

## Latest checkpoint

- `android-new` currently points to pushed commit `106c5184c`, which includes the earlier upstream merge `ac39fc2db` (upstream commit `814b6c3c0`), launcher/storage fixes and renderer/MO restoration. Untracked `out/` belongs to the user; preserve it.
- Device log proves startup SIGKILL cause: Rust's app-icon preference called `VeraActivity.applyAppIconOnUi`, which flipped `LauncherRetail`/`LauncherVera`, called `finishAndRemoveTask`, then `Process.killProcess`. The app is Iron Harbor-branded, so the Android variant-switching path and Android VERA Icon controls were removed; one Iron Harbor launcher alias remains. Validate this on Actions and then on-device.
- Separate shutdown crash is not fixed. After AAudio close, CheckJNI reports Thread-5 using main-thread `JNIEnv*` in `DeleteGlobalRef`; the captured tombstone backtrace is an HWUI `CommonPool` worker waiting on a destroyed mutex. Rust Android entry uses `android-activity 0.6.0`; audio uses `rodio 0.22.2` -> `cpal 0.17.3` and CPAL's Android AAudio/JNI path. Trace ownership at shutdown; do not apply a speculative lifecycle/audio workaround.
- Diagnostic reports launcher preflight in 66 ms and vanilla YR config from existing `Documents/RA2Harbor/Config/config.toml`. This does not verify mod switching or renderer support.
- Campaign/maps changes are committed and run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed full debug APK validation. Current run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) validates the pushed mod-diagnostics change. Continue with on-device mod/profile/MO validation, SAF fallback, renderer support and shutdown ownership.

## Current checkout

- `E:\VERA20K-ANDROID`, branch `android-new`; fork remote `vera20k-fork` is `shietgrupe-cpu13/IronHarborMobile`, upstream remote `origin` is `YuriPlanet/vera20k`.
- HEAD `106c5184c` is pushed. The latest run [46007825583](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46007825583) failed because the final MO test filter used the wrong Rust module path; no APK was produced. A workflow-only correction is staged locally. The untracked `out/` tree is user data; leave it untouched.
- No local Rust/Cargo/Gradle/APK compilation. Commit and push focused changes, validate with GitHub Actions, and inspect its logs/artifacts.

## Architecture decisions

- Preserve the legacy Iron Harbor launcher GUI and `/storage/emulated/0/Documents/Ra2Harbor/` structure; do not replace the launcher with a generic screen.
- Reuse legacy spelling/data locations (`RA2Harbor`, `IronHarborRA2YR`, `RA2IRONHARBOR`) when present. Never create a second data root when a legacy one can be reused. Do not delete or overwrite user files.
- Prefer direct game-folder access without copying the retail installation, but All files access is optional, informed and revocable. Keep upstream SAF copy as fallback. SAF `content://` URIs cannot be passed to Rust path-based archive reads.
- Use the Rust engine's archive/config resolution as authority. Do not create Kotlin-side INI semantics.
- The engine owns one GameActivity/winit event loop and simulation control-group semantics. Do not add a second loop or duplicate simulation logic.
- Engine source has a `GraphicsAPI=opengl` option resolved to wgpu GL. Android launcher selection remains unimplemented until the Android backend is confirmed compiled and device-tested; do not call it a GLES fallback yet.
- CI build success is separate from device/gameplay verification. No public release or external upstream PR is authorized by the master plan.

## Current WIP and next action

The committed integration restores the legacy shared-root helper, profile-aware RA2/YR archive checks, canonical-plus-legacy config resolution and mod-only shared Ares precedence. Run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed campaign removal, `Configs/Maps` migration, ARM64 build and APK validation. Run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) validates the follow-on mod-resolver diagnostics and duplicate-write removal. No device validation has occurred.

Committed in `39a49777f` and pushed in `6afcd6798`: launcher preflight now reports the exact config path, mod path, effective INI names, registered startup archives and mounted `ares.mix` source from the same `AssetManager` used for validation. Mod-folder selection no longer repeats manifest validation/config publication, and the TOML-only summary that could misstate manifest-resolved files is gone. This does not establish MO gameplay compatibility. Validate through run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) and a later device test.

JNI inventory: all seven `LauncherActivity` external functions have corresponding Rust exports. `VeraActivity` picker/import callbacks are dynamically registered and its other importer/network functions have matching exports. The HUD uses GameActivity's standard key callbacks. `CameraZoomSettingsService.nativeApplyCameraZoomLimits` is the one orphan declaration found: no current Rust export, manifest registration or call site; keep it dormant and record it as unsupported pending an independent migration.

Next, connect the main launcher's explicit SAF copy fallback to the existing importer without changing the normal read-in-place path. The legacy `VeraActivity` importer exists but is not currently a launcher route. Continue Phase 3–5 afterward; use Actions only for compilation/tests.

## Known manual gate

User reported a prior APK installed and showed native startup/UI/HUD, then failed game startup with archive `EACCES` and no game path configured. Device/API/build SHA and failing path are unknown. Record new device tests only from actual user/device evidence. Current local source changes are not a verified fix until Actions passes and the player retests.
