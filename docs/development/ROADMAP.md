# Iron Harbor Mobile development roadmap

Updated 2026-11-27. Authoritative specification: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md). Integration branch: `android-new`.

## Phase 2 — Build and distribution

**In progress.** GitHub Actions validates Kotlin/JVM before the ARM64 Rust build, packages an ARM64 debug APK, and uploads a 14-day artifact. The last recorded successful run is [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106); it predates the current storage work. APK identity, ABI, ZIP/ELF alignment, signature and source metadata are not yet audited in the workflow. Persistent signing and public releases remain pending; do not create or publish signing secrets without the user's approval. Review internal PR #1 before relying on the scheduled sync from default `main`.

## Phase 3 — Original launcher, storage and configuration

**Storage integration in progress; original launcher GUI not yet migrated.** The current engine is still upstream `GameActivity`, not the original Iron Harbor launcher. Current local changes add optional, informed All files access; a primary-volume SAF-folder-to-path mapping; engine-owned installation validation; direct game-file selection; a SAF copy fallback; persisted data-root selection; and config lookup in the legacy Android `Config` location. JVM and Rust regression tests have been added but are not run locally. CI must validate the current candidate before any claim of completion.

The canonical data root is `Documents/Ra2Harbor`. Reuse an existing `RA2Harbor`, `IronHarborRA2YR`, or `RA2IRONHARBOR` directory before creating a new root. Never move/delete user data. Direct access is optional and explicitly disclosed; SAF remains the fallback. Existing installed game files are read in place when direct access is used.

Remaining work: complete review/CI for storage and config-root selection; preserve/migrate existing private app data safely; restore the original launcher GUI and lifecycle; then migrate mod/profile selection, map management, diagnostics and only supported renderer controls. Current upstream documents Vulkan-only Android support; do not expose a GLES selector until the engine actually supports it.

## Phase 4 — Dynamic in-game HUD

**Not started on `android-new`.** The branch has the upstream movable HUD, but not the legacy editor/settings feature set. Migrate a schema-backed, user-extensible in-game editor, per-button appearance and bindings, profile persistence and reliable multi-touch/lifecycle behavior through the existing GameActivity/winit input owner. Do not add a second event loop or duplicate engine-owned control-group semantics.

## Phase 5 — Upstream contribution preparation

**Not started.** Compare each completed mobile feature against current upstream; separate reusable behavior from Iron Harbor branding and distribution. Prepare small tested candidate descriptions/branches only after the relevant feature is stable. External PRs require approval.

## Guardrails and next order

1. Finish current storage/config-root slice and pass GitHub Actions.
2. Restore the original Iron Harbor launcher GUI without replacing it with a generic UI.
3. Restore launcher profile/mod/map/file management using Rust's resolver as authority.
4. Restore supported renderer settings and diagnostics.
5. Implement the dynamic in-game HUD and profile editor.
6. Prepare upstream contribution candidates.

No local Rust/Cargo/Gradle/APK builds. Do not modify the legacy reference or the untracked `out/` directory. CI success is not device/gameplay validation; the partial user-reported device result remains open in `DEVICE_TEST_MATRIX.md`.
