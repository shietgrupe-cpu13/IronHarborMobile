# Iron Harbor Mobile handoff

Updated 2026-11-27. Read [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md) as the authoritative specification.

## Active checkpoint — launcher stalls, MO and shutdown

- Current checkout: `E:\VERA20K-ANDROID`, branch `android-new`, pushed HEAD `db2917dd578b3b3727346d5e61e8131df04ffd5c`; one follow-up source/doc change is uncommitted. Untracked `out/` is user data and must remain untouched.
- Commit `db2917dd5` is pushed, but Actions run [46023643484](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46023643484) failed in the nested MO map regression after Kotlin/JVM checks passed. It found the catalog row but `local_map_path_in` could not resolve nested directories differing in case (`MapsMO/Standard` vs installed `mapsmo/standard`); Android/Linux filesystems are case-sensitive. No APK was produced.
- Root-cause evidence: the 26,312 ms Play preflight constructed an `AssetManager`, indexing selected archives before engine startup repeated that work. Current changes defer archive indexing to engine startup, move launcher refresh/mod/profile selection off the UI thread, record operation timings, add a cancel affordance and skip identical config writes.
- MO changes infer profile from manifest Id/Name and discover the loose map catalog by `[MultiMaps]` contents, validating nested relative paths before loading. Focused tests and workflow commands were added. MO mode/roster behavior and actual loaded resources remain unverified.
- JNI/lifecycle changes avoid owning the loader's Activity global reference, clear the retained AndroidApp on normal native return, delay process retirement, and dispatch folder dialogs/pickers to Android's UI thread. Kotlin/JVM CI passed; Rust regression failed before ARM64/APK validation. No device validation has run; do not report the shutdown crash fixed.
- A shared map resolver fix is now in the worktree: resolve all components from the nearest existing directory using the existing case-insensitive directory lookup. Push as a follow-up and require run #24 to pass the new production loader regression before ARM64/APK validation. Then install and test on the POCO with equivalent game/mod/profile. Do not compile Rust, Gradle or APK locally.

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
