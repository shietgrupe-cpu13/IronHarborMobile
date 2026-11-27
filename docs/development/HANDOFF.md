# Iron Harbor Mobile handoff

Updated 2026-11-27. Read [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md) as the authoritative specification.

## Current checkout

- `E:\VERA20K-ANDROID`, branch `android-new`; fork remote `vera20k-fork` is `shietgrupe-cpu13/IronHarborMobile`, upstream remote `origin` is `YuriPlanet/vera20k`.
- HEAD `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e`; upstream `main` fetched at `d1e28004aa17dc445cbfee450cfe6fa55c5fe949` (11 ahead / 0 behind at last fetch).
- Uncommitted source changes implement the shared-storage/config-root integration described in `PROGRESS.md`. Do not reset or overwrite them. The untracked `out/` tree is user data; leave it untouched.
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

The local source diff adds primary-volume path resolution and validation, explicit storage permission UI, direct and SAF-copy flows, config publication under Android's `Config/`, and a native data-root pointer consumed at startup. Regression tests were added, not run. First finish a source-level review for permission revocation, atomic publication and config precedence. Run only non-build checks locally (`git diff --check`, targeted source search, Rust formatting on edited leaves); no compiler/test runner.

Then update docs with exact diff, commit and push to `android-new`, monitor the workflow and fix real failures. Verify the uploaded APK artifact's presence/metadata against its exact source SHA. Once storage is CI-green, restore the original Iron Harbor launcher GUI and lifecycle from the legacy reference selectively; continue with mod/map management, renderer/diagnostics, dynamic HUD, and Phase 5 upstream candidates.

## Known manual gate

User reported a prior APK installed and showed native startup/UI/HUD, then failed game startup with archive `EACCES` and no game path configured. Device/API/build SHA and failing path are unknown. Record new device tests only from actual user/device evidence. Current local source changes are not a verified fix until Actions passes and the player retests.
