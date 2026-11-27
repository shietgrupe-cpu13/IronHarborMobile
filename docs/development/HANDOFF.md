# Iron Harbor Mobile handoff

Updated 2026-11-27. Read [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md) as the authoritative specification.

## Latest checkpoint

- `android-new` is at pushed commit `ebc52b3fd`, including upstream `4fba96a54`, icon fix `4933e78d0`, launcher campaign-navigation removal and the `Configs/Maps` migration. Untracked `out/` belongs to the user; preserve it.
- Device log proves startup SIGKILL cause: Rust's app-icon preference called `VeraActivity.applyAppIconOnUi`, which flipped `LauncherRetail`/`LauncherVera`, called `finishAndRemoveTask`, then `Process.killProcess`. The app is Iron Harbor-branded, so the Android variant-switching path and Android VERA Icon controls were removed; one Iron Harbor launcher alias remains. Validate this on Actions and then on-device.
- Separate shutdown crash is not fixed. After AAudio close, CheckJNI reports Thread-5 using main-thread `JNIEnv*` in `DeleteGlobalRef`; the captured tombstone backtrace is an HWUI `CommonPool` worker waiting on a destroyed mutex. Rust Android entry uses `android-activity 0.6.0`; audio uses `rodio 0.22.2` -> `cpal 0.17.3` and CPAL's Android AAudio/JNI path. Trace ownership at shutdown; do not apply a speculative lifecycle/audio workaround.
- Diagnostic reports launcher preflight in 66 ms and vanilla YR config from existing `Documents/RA2Harbor/Config/config.toml`. This does not verify mod switching or renderer support.
- Current campaign/maps changes are committed; Actions run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) is building focused regressions, then ARM64/APK validation. Continue with effective mod/profile/MO resolution, SAF fallback, renderer support and shutdown ownership.

## Current checkout

- `E:\VERA20K-ANDROID`, branch `android-new`; fork remote `vera20k-fork` is `shietgrupe-cpu13/IronHarborMobile`, upstream remote `origin` is `YuriPlanet/vera20k`.
- HEAD `ebc52b3fd` is pushed; Actions run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) is in progress. The untracked `out/` tree is user data; leave it untouched.
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

The committed integration restores the legacy shared-root helper, profile-aware RA2/YR archive checks, canonical-plus-legacy config resolution and mod-only shared Ares precedence. Prior Actions run [45981409504](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45981409504) passed that storage/profile/Ares baseline and APK validation. Campaign navigation removal and `Configs/Maps` path migration are committed at `ebc52b3fd`; run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) is still validating them. No device validation has occurred.

Uncommitted mod/diagnostics changes: launcher preflight now retains the Rust validation result. It reports the exact config path, mod path, effective INI names, registered startup archive names, and mounted `ares.mix` source from the same `AssetManager` construction used by preflight. Selecting a mod folder also no longer validates and publishes its config a second time; the old TOML-only summary that could misstate manifest-resolved files was removed. This does not itself establish MO gameplay compatibility. Run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) is still active, so don't push until that run finishes or is otherwise accounted for; a push cancels superseded workflows.

JNI inventory: all seven `LauncherActivity` external functions have corresponding Rust exports. `VeraActivity` picker/import callbacks are dynamically registered and its other importer/network functions have matching exports. The HUD uses GameActivity's standard key callbacks. `CameraZoomSettingsService.nativeApplyCameraZoomLimits` is the one orphan declaration found: no current Rust export, manifest registration or call site; keep it dormant and record it as unsupported pending an independent migration.

Next, connect the main launcher's explicit SAF copy fallback to the existing importer without changing the normal read-in-place path. The legacy `VeraActivity` importer exists but is not currently a launcher route. Continue Phase 3–5 afterward; use Actions only for compilation/tests.

## Known manual gate

User reported a prior APK installed and showed native startup/UI/HUD, then failed game startup with archive `EACCES` and no game path configured. Device/API/build SHA and failing path are unknown. Record new device tests only from actual user/device evidence. Current local source changes are not a verified fix until Actions passes and the player retests.
