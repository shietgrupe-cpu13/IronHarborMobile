# IronHarborMobile — Autonomous Android Development Master Plan

## 1. Objective and approved architecture

Deliver the original Iron Harbor mobile frontend on the latest compatible YuriPlanet/vera20k engine, with minimal compatibility patches. Restore the existing launcher GUI, shared storage, mod/map management and renderer selection; improve the in-game HUD into a dynamic user-extensible editor. Continue autonomously through Phases 2–5, not merely producing recommendations.

## 2. Repository guardrails

- Integration branch: `android-new` in `shietgrupe-cpu13/IronHarborMobile`. Legacy reference: `android-development`; preserve `backup/android-development-2026-11-27`.
- Verify remotes and current HEAD before changes. Fetch upstream `YuriPlanet/vera20k`; inspect current changes and existing roadmap. Never blindly merge the legacy branch, force-push, discard user changes or modify untracked `out/` data.
- Selectively migrate coherent components; prefer upstream equivalents. Preserve engine simulation, rendering, gameplay, configuration ownership and mod compatibility. Keep branding separate from reusable Android features.
- Use focused reversible commits. Do not run local Rust/Cargo/Gradle/APK compilation or build-dependent tests. Use GitHub Actions for compilation and automated tests; lightweight source inspection and Git operations are allowed locally.
- Inspect actual CI logs; fix deterministic failures rather than repeatedly rerunning. Retry transient network failures only with bounded backoff.

## 3. Phase 2 — Build and distribution

Audit existing infrastructure before duplicating work. Previously reported successful run `45948571106` uploaded an ARM64 debug APK; verify current state rather than assuming it remains current.

- Maintain upstream synchronization; review internal draft PR #1 and enable scheduled workflow on default branch through normal reviewed integration. Validate merges before pushing, stop on conflicts, report failures; do not overwrite concurrent changes.
- Run Kotlin/JVM checks before native compilation. Maintain Gradle/Cargo caching, pinned compatible SDK/NDK/toolchains and ARM64 support. Avoid duplicate builds and unnecessary recompilation.
- Upload successful APK artifacts with clear names and links. Include branch, upstream SHA, fork SHA and build date; validate package identity, ABI, native libraries, integrity, ZIP/ELF alignment and signature.
- Establish persistent signing with securely managed repository secrets and explicit approval. Never print or commit credentials. Preserve package/signing compatibility where possible; explain unavoidable reinstall or migration requirements before proceeding.
- Prepare direct-download ARM64 nightly prerelease APKs, separate from stable releases; retain workflow artifacts. Do not publish the first public release or subsequent public releases without authorization. Do not block independent work on signing/release approval.

Gate: successful CI, downloadable APK, source-to-artifact traceability, verified packaging and documented signing status. Distinguish pending credentials from completed signing.

## 4. Phase 3 — Restore original launcher, storage and configuration

Prioritize functional game launching over advanced HUD customization. Inspect the actual legacy implementation and preserve its GUI, branding, layout, navigation and usability rather than replacing it with a generic redesign. Adapt activity lifecycle and interfaces to current upstream.

### Mandatory shared-storage compatibility

Default shared-storage root must remain **`Documents/Ra2Harbor/`**, normally `/storage/emulated/0/Documents/Ra2Harbor/` on the primary Android user volume. Resolve the actual platform path rather than assuming every device uses user 0.

- Inspect and preserve the exact legacy directory hierarchy, folder names, configuration formats and organization; do not invent subfolders or rename Ra2Harbor to VERA20K/IronHarborMobile.
- Automatically detect and reuse existing game files, mods, maps, profiles, settings and other supported data. Preserve configurations through safe, non-destructive migration; create missing directories only when needed.
- Do not create a separate replacement root for android-new or duplicate existing assets unnecessarily. Never overwrite/delete user files without explicit approval. Allow alternative storage locations.
- Existing users should be able to install the new APK, grant required permissions and reuse their existing Ra2Harbor files without reorganizing or reimporting them. Verify this as a mandatory acceptance criterion, including upgrade compatibility where signing permits.

### Storage modes and startup recovery

- Preferred direct-access mode: select existing game folders, persist selection, read files in place, optionally request `MANAGE_EXTERNAL_STORAGE` with explicit informed user consent. Explain why, check availability, handle denial/revocation and unsupported volumes gracefully. Never request broad access silently or by default without user choice.
- Preserve upstream SAF copy importer as fallback for users who decline or cannot use direct access. Explain when a private copy is created. A SAF URI is not a raw native filesystem path; do not assume persisted URI grants authorize native path reads.
- Evaluate a future SAF-backed archive source without broad permission; do not undertake a major engine storage redesign without approval.
- Use engine-owned configuration/asset validation; validate required MIX archives before enabling Play, display active path and storage mode, report missing/inaccessible/invalid installations and clear recovery steps. Preserve selection across restarts.
- Investigate reported `Could not load the game archives: access was denied (os error 13)` and no configured installation. Obtain active path, failing archive, permission and import status; do not assume root cause. Record the prior partial device test: installation, native startup, UI and HUD visibility reported successful; gameplay blocked by storage. Exact device/build details remain to be verified.

### Launcher features

- Restore original launcher GUI, main screen, branding, game installation selection, Play, settings, navigation and activity lifecycle.
- Restore mod discovery, import, management, profile creation/switching directly in launcher, vanilla and compatible Ares/other supported profiles, persistent profile-specific settings, dependency and directory validation. Launch selected profile without unnecessary duplication.
- Use Rust-owned configuration/profile validation as authoritative; audit legacy JNI interfaces (including `launcher_selection_config` and `validate_launcher_selection`) against current upstream. Do not duplicate INI parsing in Kotlin.
- Restore custom map discovery/import/catalog, profile-specific availability, shared storage access, duplicate detection and invalid-map handling. Preserve user maps.
- Restore compatible campaign progress/management only after verifying current progress ownership and UX.

### Renderer and graphics

Restore the original launcher renderer changer, especially OpenGL ES. Audit actual legacy keys and current engine backend support; expose OpenGL ES, Vulkan, automatic/default or other backends only where supported. Persist preferences, identify active renderer, validate compatibility, provide safe recovery/fallback if initialization fails and report fallback explicitly. Apply backend changes at engine initialization/restart as needed, not during active gameplay. Restore supported graphics/startup configuration without inventing unsupported options.

### Diagnostics

Restore startup error reports, active path/profile/renderer, storage permission state, engine/app versions, bounded exportable diagnostic archives and clear troubleshooting. Avoid credentials and unnecessary private information in logs.

Gate: original GUI restored; existing Ra2Harbor directory reused non-destructively; game installation, profile selection, file/map management and supported renderer configuration validated; CI green; physical-device gameplay verification requested and tracked separately.

## 5. Phase 4 — Dynamic in-game floating HUD and controls

HUD editing belongs **inside the actual game**, not exclusively in the launcher. Use the existing HUD as a default preset, not a fixed limitation.

### Data-driven controls and editor

- Stable button identifiers; allow users to add, remove, duplicate, freely reposition, resize, rename, remap, show/hide and adjust individual opacity, supported shapes/colors. Allow multiple instances of supported actions.
- Live in-game editor: add buttons, drag, select, configure, preview, save/cancel, resume play. Suppress accidental gameplay commands while editing. Keep editor access recoverable even if every custom button is hidden/deleted. Respect screen boundaries, cutouts, safe areas, orientation and aspect ratios.
- Bind supported keyboard keys and engine actions: CTRL/SHIFT/ALT/ESC/SPACE, letters/numbers, control groups, select matching, attack/stop/guard, camera controls and other supported shortcuts. Support modifier chords such as CTRL+1 and appropriate tap/hold/toggle/context-sensitive behavior. No unsupported commands or automated gameplay macros.

### Profiles and persistence

Named layouts, duplicate/switch profiles, optional game/mod associations, import/export shareable layouts, default restoration. Use versioned validated schema, safe preference migration and graceful invalid/incompatible import handling. Preserve existing layouts wherever compatible.

### Input reliability and performance

- Current GameActivity/winit input routing is authoritative. Do not blindly copy the legacy Rust input queue or introduce a second event loop. Keep engine-owned control group semantics in the engine.
- Track simultaneous touches and key edges independently; handle overlapping controls, shared bindings and modifier combinations without prematurely releasing keys. Release held inputs on cancellation, focus loss, edit mode, pause and activity recreation; prevent stuck keys.
- Selectively restore camera movement/zoom, immersive fullscreen, touch interaction, control groups, modifier behavior, text input and lifecycle features where compatible.
- Avoid per-frame allocations, unnecessary redraws and blocking gameplay-thread operations. Preserve upstream performance; no unmeasured FPS claims.
- Add focused regression tests for settings, migration, input lifecycle and layout behavior where practical.

Gate: CI green, dynamic HUD and persistent profiles implemented, device checklist documented; actual touch usability and gameplay stability remain pending until tested.

## 6. Phase 5 — Upstream contribution preparation

Compare all fork changes with latest upstream; classify as already upstream, reusable Android improvement, upstream candidate, Iron Harbor branding/distribution, compatibility patch or obsolete. Prepare independent focused feature branches with minimal diffs, tests, documentation, compatibility considerations, known limitations and proposed PR titles/descriptions. Candidates include dynamic HUD/editor/persistence, input lifecycle, launcher profile switching, file handling, renderer configuration, diagnostics and CI. Separate branding. Do not submit external PRs without approval. Remove redundant fork patches only after equivalent upstream behavior is verified.

Gate: independent reviewable contribution candidates prepared; no unauthorized external PRs.

## 7. Autonomous execution loop

For each milestone: inspect repository and handoff; fetch relevant upstream; select next incomplete task; audit legacy/current APIs; implement smallest coherent change; add tests; source-review; commit/push; trigger or observe GitHub Actions; inspect actual results/logs; fix failures; verify APK artifact; update documentation; continue. Avoid conflicting simultaneous pushes or workflows. Do not mark a task complete merely because a commit exists or a build was triggered. A green build is not playable gameplay. If a manual gate blocks one task, continue independent work. Stop repeated deterministic failures and investigate architecture. Respect usage/session limits; checkpoint before interruption.

## 8. Persistent project memory

Maintain `docs/development/ROADMAP.md`, `PROGRESS.md`, `HANDOFF.md`, `UPSTREAM_TRACKING.md`, and `DEVICE_TEST_MATRIX.md` under the same directory. Record completed/pending tasks, exact commits, workflow URLs, APK links, bugs, decisions, compatibility risks, upstream status, manual blockers and next actionable task. Distinguish source-audited, implemented, CI-verified and device-verified states. Keep docs aligned with actual results; another session must resume without chat history.

## 9. Device verification matrix

Track APK install/update, original launcher startup, permissions granted/denied/revoked, direct-path access, reuse of existing Documents/Ra2Harbor hierarchy without duplication or data loss, SAF fallback, persisted installation, mod discovery/switching, map imports, OpenGL ES and other supported backends, actual gameplay startup, HUD editing/add/delete/duplicate, bindings/chords, simultaneous touches, saved layouts/import/export, focus/pause/resume, long-session stability and diagnostic exports. Record device/API, artifact SHA, screenshots/error messages and observed results where available. Never fabricate physical-device testing.

## 10. Approval boundaries

Ask only when needed for signing credentials/secrets, destructive operations, major architecture changes beyond this plan, external upstream PRs, public releases or necessary device feedback. Continue independent work while waiting. Approved direction: original GUI; same Ra2Harbor root; optional direct-path access with informed consent; SAF fallback; launcher mod/map/file management and supported OpenGL ES selection; in-game dynamic HUD; upstream compatibility; remote CI only. No approval to delete user data, bypass Android permission controls or publish externally.

## 11. Priority order and final deliverable

1. Complete remaining build/distribution infrastructure (do not stall independent work on signing).
2. Restore original launcher GUI and lifecycle.
3. Restore existing Ra2Harbor read-in-place access and installation validation; resolve reported storage startup failure.
4. Restore mod profiles, file and map management.
5. Restore renderer selection, graphics and diagnostics.
6. Implement dynamic in-game floating HUD, profiles and input reliability.
7. Restore remaining compatible mobile features.
8. Prepare independent upstream contributions.

Final experience: open Iron Harbor, reuse Documents/Ra2Harbor, select existing game installation and mod, choose supported renderer, launch and customize floating controls during gameplay. Maintain green CI, minimal upstream divergence and preserved user data. Begin immediately by reconciling current repository state and existing roadmap with this master plan; continue through all automatable work in Phase 5, recording manual gates honestly.