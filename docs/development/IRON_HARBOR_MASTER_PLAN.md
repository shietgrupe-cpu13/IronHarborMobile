# IronHarborMobile — Autonomous Android Development Master Plan

## 1. Objective and approved architecture

Deliver the original Iron Harbor mobile frontend on the latest compatible YuriPlanet/vera20k engine, with minimal compatibility patches. Restore the existing launcher GUI, shared storage, mod/map management and renderer selection; improve the in-game HUD into a dynamic user-extensible editor. Continue autonomously through Phases 2–5, not merely producing recommendations.

**Architectural clarification:** IronHarborMobile is the Android frontend and mobile distribution of VERA20K, not a rewritten or heavily modified engine. Treat current upstream VERA20K source, directory structure, engine architecture, rendering, simulation, AI, gameplay and existing functionality as authoritative. Restore the original Iron Harbor launcher, branding/logo, GUI/navigation, file and mod management, renderer selection, and `Documents/RA2Harbor` storage directly from `android-development`; adapt only incompatible interfaces. Do not recreate working features, duplicate implementations, refactor the engine, or port unrelated gameplay changes. Keep HUD editing inside the game and extend the existing HUD with additional customizable floating buttons. Preserve upstream attribution and licensing.

### Current checkpoint — 2026-11-28

- Latest fully validated source `8e02d79b80eedca143c1c900bd5a7eb45fb83675` passed Kotlin/JVM tests, focused Rust regressions, ARM64 optimized `fast` compilation, signed debug APK assembly, identity/signature and ZIP/ELF alignment checks in Actions run [46050368117](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117). This is build-only evidence; no device/gameplay test occurred.
- Artifact [15225361525](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117/artifacts/15225361525), `IronHarborMobile-android-new-arm64-8e02d79b80eedca143c1c900bd5a7eb45fb83675`, is 54,083,163 bytes (Actions artifact digest `aecb19d0f1b9c09f91973f5bd2d9ea6ec452d32a02545acd03fa5827b0bb8a0e`), available through 2026-12-11. Package/signature/alignment verification and artifact upload passed.
- HUD label follow-up commit `57c066a9b119629bcdfcba71ff3c88eb7aa14829` is pushed; run [46053869832](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832) is in progress. It adds rename for existing HUD actions and backup schema v5, while accepting versions 1–4. No artifact or device validation exists for this commit yet.

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

### Critical device regressions — November 27, 2026

Diagnostic archive: https://drive.google.com/file/d/1YvJwZE1SRxTkWv4ErlRFQVqWM4cNL0VA/view?usp=drivesdk

Reported device: Xiaomi 2412DPC0AG (rodin), Android 16/API 36, ARM64, org.ironharbor.mobile.debug, version 0.4.3/code 207. Reported successful direct archive access, vanilla startup, intro, audio, main and skirmish menus; preflight approximately 66 ms. Reopen the archive and independently verify evidence; actual in-match gameplay is not established.

- Investigate reported SIGKILL from VeraActivity.applyAppIconOnUi (line 900), Process.killProcess(). Preserve fixed Iron Harbor branding; remove unnecessary dynamic icon switching and prevent icon updates terminating gameplay. Audit activity aliases and launcher/game process transitions.
- Investigate reported SIGABRT during shutdown: Thread-5 using JNIEnv* from main in DeleteGlobalRef, plus destroyed pthread mutex errors. Trace actual owner through GameActivity/android-activity, JavaVM attachment, JNI reference ownership, worker joins, rendering/audio teardown and callbacks. Correct lifetime and thread ordering; never suppress CheckJNI or use forced termination as a workaround.
- Latest source trace found the 26,312 ms Play preflight came from constructing/indexing an `AssetManager` in JNI selection validation before engine startup indexed the same archives again. Keep launcher selection/preflight limited to paths and lightweight configuration; let engine startup resolve/index resources once. Move launcher scans/config publication off the UI thread and record selection/preflight stage timings. This source path is included in later CI-validated commits; no equivalent-device total-startup/mod/profile timing retest is recorded, so do not claim the reported stall is fixed.
- Device user reports mod switching does not change loaded gameplay. Trace launcher selection, nativeConfigForMod/nativeConfigForSelection, Rust config generation, atomic publication, persisted managed/external paths, root pointer, engine config precedence, asset manager construction, archive mounting and stale state across sessions. Verify RA2/YR/Mental Omega/other supported mods and return to vanilla, managed/external transitions and restart persistence. Do not confuse UI selection with effective engine state.
- MO-specific investigation found `mod.ini` identity was not parsed for launcher profile selection, and the installed MO map catalog is a loose `ini/*.ini` containing nested relative map paths. Current source recognizes Mental Omega from manifest Id/Name and discovers nested maps from catalog contents rather than a fixed INI filename; focused regressions and ARM64 packaging passed in run `46046233771`. Verify selected MO profile/resources on device; mode/roster compatibility remains a separate open check.
- Historical CI caught both case-sensitive nested-map lookup and an incomplete synthetic map fixture. The resolver and fixture corrections are included in later commits; focused map regressions passed in run `46046233771`. Verify that Red Resurrection's nested maps appear and launch on device before declaring the user report closed.
- Preserve a meaningful Mental Omega selection using supported interfaces; the source now recognizes MO identity from the manifest and routes it through the supported profile resolver. CI coverage does not prove that an installed MO build loads its intended rules/art/AI/sound/EVA/modes/maps or Ares precedence. Verify those effective resources in-game; mode/roster compatibility remains open.
- Review android-development commit history, not only its final snapshot. Preserve intentional removals and streamlined branding/navigation. Remove duplicate navigation, redundant directory/mod actions, obsolete settings, nonfunctional buttons, debug placeholders, unnecessary icon controls and redundant HUD editors once equivalent in-game functionality exists. Keep essential Play, installation, mods/profiles, renderer/graphics, maps, permissions and diagnostics in their appropriate pages. Do not remove useful functionality merely to reduce button count.
- Correct custom maps to **Documents/RA2Harbor/Configs/Maps/**, explicitly required by the owner. Audit initialization, import, discovery, nativeMapsDirectory/JNI, engine map loading, config references and tests. Reconcile root-level Maps non-destructively, without overwriting, deleting or creating duplicate active map libraries. Verify legacy Config versus Configs naming; the existing config.toml/VERA.INI location below is a compatibility reference, not authorization to create a second configuration tree. Preserve actual existing configuration files and resolve naming consistently.


Prioritize functional game launching over advanced HUD customization. Inspect the actual legacy implementation and preserve its GUI, branding, layout, navigation and usability rather than replacing it with a generic redesign. Adapt activity lifecycle and interfaces to current upstream.

### Mandatory shared-storage compatibility

Reuse the complete existing `android-development` shared-storage architecture; do not reinvent it. The canonical fresh-install root is **`Documents/RA2Harbor/`**, normally `/storage/emulated/0/Documents/RA2Harbor/` on the primary Android user volume. Resolve the actual platform path rather than assuming every device uses user 0. Preserve this hierarchy and exact filenames:

```text
Documents/RA2Harbor/
  Mods/
    AresSupport/
  Configs/
    Maps/
  Saves/
  Logs/
  Config/
    config.toml
    VERA.INI
```

- Reuse existing `RA2Harbor`, `Ra2Harbor`, `IronHarborRA2YR` and `RA2IRONHARBOR` roots without splitting, copying or losing data. Preserve `ironharbor_user_storage` preferences and migrate compatible selected roots and game paths. Keep game installation source separate from writable engine data root.
- Allow an existing game installation anywhere accessible on local shared storage; selecting it reads assets in place and never copies/moves them. Detect base RA2 and YR archive sets; `ra2md.mix` is not required for base RA2.
- Reuse zero-copy mod selection: managed `Mods` children resolve to relative paths; external mods resolve to canonical absolute paths. Do not copy selected mods. Use the Rust config resolver/validator and atomically publish engine-generated config.
- Restore RA2, YR, MO, AUTO and CUSTOM profiles only where current engine support exists. Preserve shared `Mods/AresSupport` management, per-mod archive precedence and vanilla isolation.
- Restore alternative shared-data-root selection, persistent source selection, direct path browsing and permission recovery. Revoked permission must not silently redirect or create a second root; report the inaccessible selected path and provide a recovery action.
- Retain SAF copy import only as an explicitly chosen fallback, not the normal existing-game workflow. Never treat a `content://` URI as a Rust path.
- Restore legacy map importer/catalog behavior, including recursive discovery where supported, collision protection, supported extensions and safe path validation. Migrate old private configuration, saves, maps, mods and logs non-destructively.
- Never overwrite/delete user files without approval. Existing users must reuse their `Documents/RA2Harbor` hierarchy without reorganization or reimport; validate this on device where possible.

### Storage modes and startup recovery

- Preferred direct-access mode: select existing game folders, persist selection, read files in place, optionally request `MANAGE_EXTERNAL_STORAGE` with explicit informed user consent. Explain why, check availability, handle denial/revocation and unsupported volumes gracefully. Never request broad access silently or by default without user choice.
- Preserve upstream SAF copy importer as fallback for users who decline or cannot use direct access. Explain when a private copy is created. A SAF URI is not a raw native filesystem path; do not assume persisted URI grants authorize native path reads.
- Evaluate a future SAF-backed archive source without broad permission; do not undertake a major engine storage redesign without approval.
- Use engine-owned configuration/asset validation; validate required MIX archives before enabling Play, display active path and storage mode, report missing/inaccessible/invalid installations and clear recovery steps. Preserve selection across restarts.
- Investigate reported `Could not load the game archives: access was denied (os error 13)` and no configured installation. Obtain active path, failing archive, permission and import status; do not assume root cause. Record the prior partial device test: installation, native startup, UI and HUD visibility reported successful; gameplay blocked by storage. Exact device/build details remain to be verified.

### Launcher features

- Restore original launcher GUI, main screen, branding, game installation selection, Play, settings, navigation and activity lifecycle.
- Restore mod discovery, import, management, profile creation/switching directly in launcher, vanilla and compatible Ares/other supported profiles, persistent profile-specific settings, dependency and directory validation. Launch selected profile without unnecessary duplication; preserve zero-copy paths and Ares archive precedence described above.
- Use Rust-owned configuration/profile validation as authoritative; audit legacy JNI interfaces (including `launcher_selection_config` and `validate_launcher_selection`) against current upstream. Do not duplicate INI parsing in Kotlin.
- Restore custom map discovery/import/catalog, profile-specific availability, shared storage access, duplicate detection and invalid-map handling. Preserve user maps.
- Remove Campaigns from the top navigation and remove `CAMPAIGNS / RA2 + YURI'S REVENGE` from Home. Remove the exclusive launcher campaign page and unused navigation logic; do not relocate or replace these shortcuts. Preserve engine-side campaign functionality through the in-game interface.

### Renderer and graphics

Restore the original functional launcher renderer changer, especially OpenGL ES. Audit legacy nativeGetRenderer/nativeSetRenderer, Rust configuration, Cargo features, Android build dependencies and backend initialization. If current upstream lacks GLES, investigate selective restoration of the legacy integration without sacrificing upstream optimizations. Persist and consume the selected backend at startup; verify actual backend, not just UI labels. Restore compatible render scale/resolution, graphics quality, camera zoom limits and immersive settings; resolve missing JNI implementations before exposing controls. Audit actual legacy keys and current engine backend support; expose OpenGL ES, Vulkan, automatic/default or other backends only where supported. Persist preferences, identify active renderer, validate compatibility, provide safe recovery/fallback if initialization fails and report fallback explicitly. Apply backend changes at engine initialization/restart as needed, not during active gameplay. Restore supported graphics/startup configuration without inventing unsupported options.

### Diagnostics

Restore startup error reports, requested versus effective mod/profile/renderer, resolved mod path, actual config source, mounted archives and Ares source, actual Configs/Maps path, backend initialization/fallback, lifecycle and shutdown events, active path/profile/renderer, storage permission state, engine/app versions, bounded exportable diagnostic archives and clear troubleshooting. Avoid credentials and unnecessary private information in logs.

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

Track APK install/update, original launcher startup, permissions granted/denied/revoked, direct-path access, reuse of existing Documents/RA2Harbor hierarchy without duplication or data loss, SAF fallback, persisted installation, mod discovery/switching, map imports, OpenGL ES and other supported backends, actual gameplay startup, HUD editing/add/delete/duplicate, bindings/chords, simultaneous touches, saved layouts/import/export, focus/pause/resume, long-session stability and diagnostic exports. Record device/API, artifact SHA, screenshots/error messages and observed results where available. Never fabricate physical-device testing.

## 10. Approval boundaries

Ask only when needed for signing credentials/secrets, destructive operations, major architecture changes beyond this plan, external upstream PRs, public releases or necessary device feedback. Continue independent work while waiting. Approved direction: original GUI; same Ra2Harbor root; optional direct-path access with informed consent; SAF fallback; launcher mod/map/file management and supported OpenGL ES selection; in-game dynamic HUD; upstream compatibility; remote CI only. No approval to delete user data, bypass Android permission controls or publish externally.

## 11. Priority order and final deliverable

1. Review legacy history and latest diagnostics; reconcile current repository state.
2. Fix icon-triggered termination and JNI shutdown/resource lifetime failures.
3. Fix actual mod switching and restore Mental Omega-compatible selection.
4. Remove both Campaigns buttons and exclusive launcher campaign navigation; correct Configs/Maps.
5. Restore functional renderer switching, investigate GLES integration, and restore compatible graphics/camera settings.
6. Protect working direct storage and Ares behavior; complete remaining launcher cleanup and SAF fallback.
7. Maintain CI/distribution infrastructure without stalling independent work on signing.
8. Implement dynamic in-game HUD, profiles and input reliability.
9. Prepare independent upstream contributions.

Batch independent fixes where practical to avoid unnecessary CI builds. Add focused tests for config precedence, mod metadata and switching, vanilla/Ares isolation, map path/migration, renderer persistence and consumption, lifecycle ownership and packaging. Device acceptance must include both Campaigns buttons absent, Configs/Maps discovery/import, actual mod and renderer changes, persistence, starting a skirmish match, clean exit and repeated relaunch. Record device evidence separately from CI; never claim device success from compilation.

Final experience: open Iron Harbor, reuse Documents/RA2Harbor, select existing game installation and mod, choose supported renderer, launch and customize floating controls during gameplay. Maintain green CI, minimal upstream divergence and preserved user data. Begin immediately by reconciling current repository state and existing roadmap with this master plan; continue through all automatable work in Phase 5, recording manual gates honestly.
