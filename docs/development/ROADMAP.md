# Iron Harbor Mobile development roadmap

Updated 2026-11-27. Authoritative specification: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md). Integration branch: `android-new`.

## Phase 2 — Build and distribution

**Debug artifact gate passes.** Run [45981409504](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45981409504) passed Kotlin/JVM, focused Rust regressions, ARM64 build, debug APK package identity (`org.ironharbor.mobile.debug`), signing, APK ZIP alignment, ARM64 ELF 16 KB LOAD alignment, and artifact upload. Artifact link and source revisions are in `PROGRESS.md`. Release signing and public releases remain pending; do not create or publish signing secrets without the user's approval. Review internal PR #1 before relying on the scheduled sync from default `main`.

## Phase 3 — Original launcher, storage and configuration

**Storage integration baseline passes hosted validation.** The restored launcher uses the shared-root helper, direct read-in-place game selection, profile-aware RA2/YR validation, canonical and legacy config-file selection, and mod-only shared Ares precedence. Actions run [45981409504](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45981409504) passed Kotlin/JVM tests, focused Rust regressions, ARM64 compilation, and validated debug APK artifact upload. Device behavior remains unverified. The existing SAF copier is not yet connected as an explicit fallback from the main launcher.

The canonical fresh data root is `Documents/RA2Harbor`. Reuse existing `Ra2Harbor`, `IronHarborRA2YR`, or `RA2IRONHARBOR` folders before creating it. Never move/delete user data. Direct access is optional and explicitly disclosed; existing game files are read in place. Shared Ares support applies only with a selected mod; the mod's own `ares.mix` takes precedence, and vanilla cannot see the shared archive.

Remaining work: connect launcher SAF-copy fallback; test private-data migration and profile transitions; physical-device checks for revoked permissions and direct read access; then complete launcher mod/map/config, diagnostics and supported renderer workflows. Current upstream documents Vulkan-only Android support; do not expose a GLES selector until the engine actually supports it.

## Phase 4 — Dynamic in-game HUD

**Not started on `android-new`.** The branch has the upstream movable HUD, but not the legacy editor/settings feature set. Migrate a schema-backed, user-extensible in-game editor, per-button appearance and bindings, profile persistence and reliable multi-touch/lifecycle behavior through the existing GameActivity/winit input owner. Do not add a second event loop or duplicate engine-owned control-group semantics.

## Phase 5 — Upstream contribution preparation

**Not started.** Compare each completed mobile feature against current upstream; separate reusable behavior from Iron Harbor branding and distribution. Prepare small tested candidate descriptions/branches only after the relevant feature is stable. External PRs require approval.

## Guardrails and next order

1. Fix the diagnostic-confirmed Android launcher-alias SIGKILL (source change pending Actions/device retest).
2. Trace the separate AAudio/JNI/HWUI shutdown crash from its actual owner; do not guess from the partial tombstone.
3. Remove both launcher campaign shortcuts/pages and correct Maps to `Documents/RA2Harbor/Configs/Maps/` non-destructively.
4. Restore effective mod selection and supported Mental Omega configuration through the engine resolver; verify actual selected resources.
5. Restore a renderer selector only for backend(s) actually supported and consumed by the current engine, then finish launcher cleanup.
6. Continue storage, diagnostics, dynamic in-game HUD and upstream proposal work; track physical-device gates separately.

No local Rust/Cargo/Gradle/APK builds. Do not modify the legacy reference or the untracked `out/` directory. CI success is not device/gameplay validation; the partial user-reported device result remains open in `DEVICE_TEST_MATRIX.md`.
