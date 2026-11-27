# Iron Harbor Mobile development roadmap

Updated 2026-11-27. Primary integration branch: `android-new`.

## Phase 2 — Build and distribution infrastructure

**In progress.** `android-new` is based on current VERA20K `main` and has a GitHub Actions workflow for scheduled/manual upstream sync and Android validation. It runs Kotlin compilation and JVM tests before native compilation; builds `aarch64-linux-android` with the `fast` profile; packages a debug APK; and now uploads the APK as a run artifact for 14 days. Rust and Gradle caches are configured. `android-new` is explicitly allowed to write its own Gradle cache scope.

Artifact gate passed on Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106); GitHub confirms the artifact is downloadable. Remaining Phase 2 work:

- Validate APK package identity, ARM64 native library, ZIP/ELF alignment, and signature before upload.
- Embed upstream SHA, fork SHA, and build identity in the artifact metadata.
- Establish persistent signing for install-over-install. This requires a protected repository secret/credential and explicit user approval; the current debug key is generated per run.
- Enable the scheduled synchronization workflow on the repository's default branch by merging internal PR #1 after reviewing its final diff and successful validation.
- Add stable/nightly release handling only after signing and source-to-artifact traceability are configured. Do not publish releases yet.

## Phase 3 — Mobile gameplay and controls

**Audit complete; migration not yet complete.** `android-new` includes a movable Android touch HUD from upstream PR #5295 (commits `302be2b45`, `a725dc756`). It supports show/hide, edit drag, normalized persisted positions, `T` select-matching, and C1-C10 recall/assign chords. The HUD's key dispatch correction is in `2e19b4453`.

The old `android-development` branch at `1293fb441727e2522f6a284d88faf4f108a1ee3e` adds settings modes/layout presets, per-button size/opacity, remapping, two control-set visibility, editor and SAF layout backup, held-key lifecycle handling, immersive mode, camera zoom, text-input hooks, and an engine-owned queued Android input path. These are source-audited features, not runtime-verified on `android-new`. The old engine queue and control-group JNI path are architecturally different; port individual behavior through current GameActivity/winit owners rather than copying the old queue or adding another event loop.

Next: port a neutral HUD settings model and persistence with schema migration/tests; then adapt the editor and input lifecycle against current engine ownership. Camera zoom and immersive mode are independent later slices. Physical device validation remains manual.

## Phase 4 — Launcher and file management

**Audit complete; migration not yet started.** The old launcher passes selection through Rust JNI validation/config publication so it agrees with gameplay; do not replace it with duplicate Kotlin INI parsing. Old direct-path storage requires `MANAGE_EXTERNAL_STORAGE`, while upstream SAF copies to app-private staging. That conflicts with the earlier read-in-place requirement; choose an engine/storage bridge before porting. The old branch also has mod/map catalogs, import jobs, campaign progress, startup reports, and bounded diagnostic ZIPs. Preserve original installations and avoid broad storage access until the conflict is resolved.

## Phase 5 — Upstream contribution preparation

**Not started.** Classify each useful Android-neutral improvement against current upstream, separate Android-specific code from Iron Harbor branding/distribution, then prepare small contribution candidates with tests and documentation. Do not open upstream PRs without approval.

## Guardrails

- Keep `android-development` and `backup/android-development-2026-11-27` intact; never merge the old branch wholesale.
- Preserve upstream simulation, rendering, mod, and gameplay behavior.
- Do not run local Rust or Gradle builds. Use GitHub Actions; inspect logs on failure.
- Do not claim device verification without a real device run.
