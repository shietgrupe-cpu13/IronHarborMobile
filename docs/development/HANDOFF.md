# Iron Harbor Mobile handoff

Updated 2026-11-27. Read [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md) as the authoritative specification.

## Current checkout

- `E:\VERA20K-ANDROID`, branch `android-new`; fork remote `vera20k-fork` is `shietgrupe-cpu13/IronHarborMobile`, upstream remote `origin` is `YuriPlanet/vera20k`.
- HEAD `913e69cbfdd69d72f0abe9c3a8463ee188b95c4c`. The latest upstream refs have not been fetched during this storage pass.
- Uncommitted changes span the launcher, shared storage, JNI/config integration, Ares archive resolver, focused tests and Actions workflow. Preserve them. The untracked `out/` tree is user data; leave it untouched.
- No local Rust/Cargo/Gradle/APK compilation. Commit and push focused changes, validate with GitHub Actions, and inspect its logs/artifacts.

## Architecture decisions

- Preserve the legacy Iron Harbor launcher GUI and `/storage/emulated/0/Documents/Ra2Harbor/` structure; do not replace the launcher with a generic screen.
- Reuse legacy spelling/data locations (`RA2Harbor`, `IronHarborRA2YR`, `RA2IRONHARBOR`) when present. Never create a second data root when a legacy one can be reused. Do not delete or overwrite user files.
- Prefer direct game-folder access without copying the retail installation, but All files access is optional, informed and revocable. Keep upstream SAF copy as fallback. SAF `content://` URIs cannot be passed to Rust path-based archive reads.
- Use the Rust engine's archive/config resolution as authority. Do not create Kotlin-side INI semantics.
- The engine owns one GameActivity/winit event loop and simulation control-group semantics. Do not add a second loop or duplicate simulation logic.
- GLES must remain unavailable unless current engine/backend support is implemented and validated; current docs say Android requires Vulkan.
- CI build success is separate from device/gameplay verification. No public release or external upstream PR is authorized by the master plan.

## Current WIP and next action

The local source diff restores the legacy shared-root helper, profile-aware RA2/YR archive checks, canonical-plus-legacy config resolution, atomic config publication without overwriting the old root file, and non-destructive private-data migration. It also ports the shared Ares archive layer through runtime initialization, process asset reconstruction and launcher preflight: shared Ares support is mod-only and a mod-owned `ares.mix` wins. Tests were added/updated but have not run. The workflow regression filters were corrected and now include config, nested-mod and Ares behavior.

JNI inventory: all seven `LauncherActivity` external functions have corresponding Rust exports. `VeraActivity` picker/import callbacks are dynamically registered and its other importer/network functions have matching exports. The HUD uses GameActivity's standard key callbacks. `CameraZoomSettingsService.nativeApplyCameraZoomLimits` is the one orphan declaration found: no current Rust export, manifest registration or call site; keep it dormant and record it as unsupported pending an independent migration.

Then run `git diff --check` and targeted static review only, commit and push the coherent source changes to `android-new`, monitor Actions and fix actual failures. No local compilation. Verify the uploaded artifact against its source SHA. The main launcher still lacks a provider-backed SAF copy fallback; the old `VeraActivity` importer remains but is not a launcher route. Continue with that gap and remaining Phase 3–5 items after CI.

## Known manual gate

User reported a prior APK installed and showed native startup/UI/HUD, then failed game startup with archive `EACCES` and no game path configured. Device/API/build SHA and failing path are unknown. Record new device tests only from actual user/device evidence. Current local source changes are not a verified fix until Actions passes and the player retests.
