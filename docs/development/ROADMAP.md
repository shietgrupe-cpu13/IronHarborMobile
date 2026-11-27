# Iron Harbor Mobile development roadmap

Updated 2026-11-27. Authoritative specification: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md). Integration branch: `android-new`.

## Latest checkpoint — workflow 46042175052 passed

`android-new` is now at `e1af36488dc270b73578fdc9166e63f97a52dc43`, including upstream merge `068e965efcf27bfccc59fab3663fe7bded3788be`. Run [46042175052](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052) passed Kotlin/JVM, all 13 focused Rust regressions, ARM64 optimized `fast` library build, APK assembly, persistent signature, package identity and ZIP/ELF alignment verification, artifact upload and upstream merge push. Debug artifact [15222025462](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052/artifacts/15222025462) is available until 2026-12-11. No device/gameplay validation was performed.

The Red Resurrection catalog registry fallback and HUD layout migration/backup tests are CI-verified. This does not verify actual RR gameplay, MO gameplay, or the expanded HUD on hardware. The full in-game HUD editor remains incomplete; the current overlay is compact and the settings preview still runs in the launcher.

### Next order

1. Integrate existing layout editing into the GameActivity HUD path and make the overlay consume its persisted layout settings; add focused JVM tests, then validate via Actions.
2. Keep startup/mod/profile latency claims evidence-based. Source confirms launcher selection and Play preflight defer archive indexing to game startup; equivalent device startup and switching measurements are still required before claiming the 26,312 ms stall was fixed.
3. Continue Red Resurrection/MO resource and map compatibility with actual resolved-resource diagnostics and POCO validation. The latest passing Rust tests validate parser/resolver paths only.
4. Trace and fix the shutdown `DeleteGlobalRef`/destroyed-mutex report at its actual owner. Do not treat the 1.2 s delayed process kill as proof of clean teardown.
5. Finish the launcher SAF fallback, config migration, supported renderer behavior and remaining launcher cleanup. Preserve direct access, existing data and the working renderer.
6. Continue full HUD work (buttons, bindings, profiles, import/export and lifecycle) and then upstream contribution preparation.

Old run `46023643484`/`46027519203`/`46007825583` notes below are historical; their failures are fixed or superseded by `46042175052`. Do not treat them as current blockers.

## Immediate regression milestone — renderer and MO profile

Commit `db2917dd5` removes duplicate archive indexing from JNI validation, moves launcher scans and mod/profile configuration off the UI thread, recognizes Mental Omega from `mod.ini`, discovers its loose nested-map catalog generically, and corrects native-thread Activity UI calls and JNI/lifecycle ownership. Kotlin/JVM checks passed, but run [46023643484](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46023643484) failed its production nested-map loader test before ARM64 packaging. A shared case-insensitive nested-path fix is in the worktree awaiting CI. Device retest is still required. Prior renderer/MO selector changes also remain device-unverified.

## Phase 2 — Build and distribution

**Debug artifact gate passes through commit `ebc52b3fd`.** Run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed Kotlin/JVM, focused Rust regressions, ARM64 native compilation, APK assembly, package identity, debug signing, ZIP alignment, 16 KB ARM64 ELF alignment, and artifact upload. It produced [IronHarborMobile-android-new-arm64-ebc52b3fdbc2dba99b60a19ff661d91bcbe8f44a](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660/artifacts/15194923757), 74,441,887 bytes, retained until 2026-12-11. It contains `org.ironharbor.mobile.debug`, version `0.4.3` / code `207`, ARM64 native library and a v2 debug signature. Upstream `814b6c3c0` is merged at `ac39fc2db`; current commit `6afcd6798` adds mod-selection diagnostics and awaits validation in run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713). Stable signing and public releases remain gated on credentials and approval.

## Phase 3 — Original launcher, storage and configuration

**Storage integration baseline passes hosted validation through `ebc52b3fd`.** The restored launcher uses the shared-root helper, direct read-in-place game selection, profile-aware RA2/YR validation, canonical and legacy config-file selection, and mod-only shared Ares precedence. Run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed JVM/Rust tests, ARM64 build, APK packaging and alignment checks. Campaign navigation removal and `Configs/Maps` migration are included. Commit `6afcd6798` captures resolved mod resources in preflight diagnostics, removes redundant mod revalidation/config writes, and removes a misleading config-only resource summary; run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) is validating it. Device behavior remains unverified. The existing SAF copier is not yet connected as an explicit fallback from the main launcher.

The canonical fresh data root is `Documents/RA2Harbor`. Reuse existing `Ra2Harbor`, `IronHarborRA2YR`, or `RA2IRONHARBOR` folders before creating it. Never move/delete user data. Direct access is optional and explicitly disclosed; existing game files are read in place. Shared Ares support applies only with a selected mod; the mod's own `ares.mix` takes precedence, and vanilla cannot see the shared archive.

Remaining work: verify actual mod/profile switching and Mental Omega resource resolution; connect launcher SAF-copy fallback; test private-data migration and profile transitions; physical-device checks for revoked permissions and direct read access; then complete launcher mod/map/config and diagnostics workflows. Engine source has a `GraphicsAPI=opengl` option resolved to wgpu GL; validate Android feature inclusion and device behavior before offering it as a launcher option. Do not describe it as a tested GLES fallback.

## Phase 4 — Dynamic in-game HUD

**Not started on `android-new`.** The branch has the upstream movable HUD, but not the legacy editor/settings feature set. Migrate a schema-backed, user-extensible in-game editor, per-button appearance and bindings, profile persistence and reliable multi-touch/lifecycle behavior through the existing GameActivity/winit input owner. Do not add a second event loop or duplicate engine-owned control-group semantics.

## Phase 5 — Upstream contribution preparation

**Not started.** Compare each completed mobile feature against current upstream; separate reusable behavior from Iron Harbor branding and distribution. Prepare small tested candidate descriptions/branches only after the relevant feature is stable. External PRs require approval.

## Guardrails and next order

1. Inspect Actions run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713) for the mod diagnostics change and verify its artifact.
2. Trace the separate AAudio/JNI/HWUI shutdown crash from its actual owner; do not guess from the partial tombstone.
3. Use the new preflight resource report to verify effective mod selection and supported Mental Omega resource resolution on-device.
4. Connect the existing SAF copy fallback from the launcher, preserving the default read-in-place path.
5. Restore a renderer selector only for backend(s) actually supported and consumed by the current engine, then finish launcher cleanup.
6. Continue diagnostics, dynamic in-game HUD and upstream proposal work; track physical-device gates separately.

No local Rust/Cargo/Gradle/APK builds. Do not modify the legacy reference or the untracked `out/` directory. CI success is not device/gameplay validation; the partial user-reported device result remains open in `DEVICE_TEST_MATRIX.md`.
