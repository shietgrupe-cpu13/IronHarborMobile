# Iron Harbor Mobile development roadmap

Updated 2026-11-28. Authoritative specification: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md). Integration branch: `android-new`.

## Latest checkpoint — workflow 46090355627 passed

Commit `f4ef8bbf1` passed run [46090355627](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627): Kotlin/JVM, focused Rust regressions, optimized ARM64 native build, debug APK assembly, persistent signature, package identity, ZIP/ELF alignment checks and artifact upload. Artifact [15241902961](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627/artifacts/15241902961) is 54,431,155 bytes, SHA-256 `e9e96beb5bc217792df712842c8fef3e197b89296f6c754263bbc1a1e40b0b15d`, expires 2026-12-12. CI then merged upstream change `c7868bc8f0756f063dd944b91a1a90d565849246`; local/remote `android-new` is `b94e433a9`. No physical-device validation occurred.

Selected-mod loose maps are staged temporarily into the canonical `Documents/RA2Harbor/Maps` directory. Prior `Configs/Maps` content is migrated non-destructively. User device testing is required to verify Choose Map visibility and safe cleanup. Do not describe this as device-verified.

## Prior checkpoint — workflow 46050368117 passed

Commit `1703630f7fd5027eaf89dea04a1f5333e88a7e62` passed run [46046233771](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771): Kotlin/JVM, focused Rust regressions, ARM64 optimized `fast` library build, APK assembly, persistent signature, package identity, ZIP/ELF alignment checks and artifact upload. Debug artifact [15222634738](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771/artifacts/15222634738) (54,082,616 bytes; SHA-256 `59d3e8d5e97947dbe79ddea60eae2f9ee7cde5504bd33235922c12ed782ae77f`) is available until 2026-12-11. No device/gameplay validation was performed.

Follow-up commit `8e02d79b8` passed the same hosted Kotlin/JVM, focused Rust, ARM64, APK signing and alignment gates. Artifact [15225361525](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117/artifacts/15225361525) is the latest debug build, 54,083,163 bytes, available until 2026-12-11. It adds per-button opacity and HUD backup format v4; physical-device behavior is still unverified.

Commit `57c066a9b` continues the HUD editor with labels for existing actions and backup schema v5. Run [46053869832](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832) passed and artifact [15226298591](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832/artifacts/15226298591) is available. Device validation remains pending.

The Red Resurrection catalog registry fallback and HUD layout migration/backup tests remain CI-verified, not device-verified. Commit `1703630f7` integrates the existing launcher layout editor and action settings into the live overlay. Commit `8e02d79b8` adds per-button opacity. The full HUD feature set remains incomplete: dynamic add/duplicate/rename, named layout profiles, extended modifier combinations and device touch reliability.

### Next order

1. Device-test artifact `15226298591`: verify rename, opacity, in-game EDIT/long-press, move/resize/show/hide, layout import/export and persistence across update; test actions, C1/C2 tap/hold, route-key release and pause/resume.
2. Continue HUD editor gaps with the shared preference owner: named profiles, arbitrary add/duplicate/rename, modifier behavior and individual appearance settings. Do not claim completion from unit tests.
3. Keep startup/mod/profile latency claims evidence-based. Equivalent device startup and switching timings are still required before claiming the reported 26,312 ms stall is fixed.
4. Continue Red Resurrection/MO resource and map compatibility with actual resolved-resource diagnostics and device validation; parser tests do not prove gameplay.
5. Trace the shutdown `DeleteGlobalRef`/destroyed-mutex report at its true JNI/audio owner. A delayed process kill is not proof of clean teardown.
6. Continue SAF fallback, config migration, supported renderer behavior and remaining launcher cleanup while preserving direct access and user data.
7. Prepare upstream contribution candidates after stable feature ownership/testing; no external PR without approval.

Old run `46023643484`/`46027519203`/`46007825583` notes below are historical; their failures are fixed or superseded by `46046233771`. Do not treat them as current blockers.

## Immediate regression milestone — renderer and MO profile

Commit `db2917dd5` removes duplicate archive indexing from JNI validation, moves launcher scans and mod/profile configuration off the UI thread, recognizes Mental Omega from `mod.ini`, discovers its loose nested-map catalog generically, and corrects native-thread Activity UI calls and JNI/lifecycle ownership. Kotlin/JVM checks passed, but run [46023643484](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46023643484) failed its production nested-map loader test before ARM64 packaging. A shared case-insensitive nested-path fix is in the worktree awaiting CI. Device retest is still required. Prior renderer/MO selector changes also remain device-unverified.

## Phase 2 — Build and distribution

**Latest debug artifact gate passes through `1703630f7`.** See workflow [46046233771](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771) and artifact [15222634738](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771/artifacts/15222634738) above. The artifact is signed with the persistent certificate and passed package/signature/alignment verification. Public release/nightly publication and source-to-release distribution remain separate approval/workflow tasks.

## Phase 3 — Original launcher, storage and configuration

**Storage integration passed its baseline CI checks; remaining behavior is not device-verified.** The launcher retains direct read-in-place game selection, profile-aware RA2/YR validation, canonical/legacy config selection and mod-only shared Ares precedence. Later commits add resolved-resource diagnostics and avoid redundant mod validation/config publication. The launcher SAF copier is still not connected as an explicit fallback from the main launcher. See the current handoff for active manual gates; do not infer playable mod behavior from build status.

The canonical fresh data root is `Documents/RA2Harbor`. Reuse existing `Ra2Harbor`, `IronHarborRA2YR`, or `RA2IRONHARBOR` folders before creating it. Never move/delete user data. Direct access is optional and explicitly disclosed; existing game files are read in place. Shared Ares support applies only with a selected mod; the mod's own `ares.mix` takes precedence, and vanilla cannot see the shared archive.

Remaining work: verify actual mod/profile switching and Mental Omega resource resolution; connect launcher SAF-copy fallback; test private-data migration and profile transitions; physical-device checks for revoked permissions and direct read access; then complete launcher mod/map/config and diagnostics workflows. Engine source has a `GraphicsAPI=opengl` option resolved to wgpu GL; validate Android feature inclusion and device behavior before offering it as a launcher option. Do not describe it as a tested GLES fallback.

## Phase 4 — Dynamic in-game HUD

**Partially integrated; device verification pending.** Commit `1703630f7` wires the existing action catalog, shared launcher layout/settings and layout editor into the GameActivity overlay. Follow-up `8e02d79b8` adds CI-verified per-button opacity with backward-compatible layout backup import. Still open: arbitrary add/duplicate/rename, named profiles, full modifier/chord remapping, and physical-device multitouch/lifecycle checks. Continue using the existing GameActivity/winit input owner; do not add another event loop or duplicate engine-owned control-group semantics.

## Phase 5 — Upstream contribution preparation

**Candidate preparation remains open.** No upstream-neutral contribution candidate or feature branch has been prepared in this checkout. Compare each stable mobile feature against current upstream, separate reusable behavior from Iron Harbor branding/distribution, and prepare small tested candidate scopes. External PRs require approval.

## Guardrails and next order

1. Device-test artifact [15222634738](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771/artifacts/15222634738), especially in-game editor entry, layout persistence, controls and input release.
2. Trace the separate AAudio/JNI/HWUI shutdown crash from its actual owner; do not guess from the partial tombstone.
3. Use the preflight resource report to verify effective mod selection and Mental Omega resources on-device.
4. Connect the existing SAF copy fallback from the launcher, preserving default read-in-place behavior.
5. Verify supported renderer selection on-device; finish safe config migration and remaining launcher cleanup.
6. Continue HUD editor functionality and upstream proposal preparation; track physical-device gates separately.

No local Rust/Cargo/Gradle/APK builds. Do not modify the legacy reference or the untracked `out/` directory. CI success is not device/gameplay validation; the partial user-reported device result remains open in `DEVICE_TEST_MATRIX.md`.
