# Upstream tracking

Updated 2026-11-27.

## Revision relationship

- Upstream: `YuriPlanet/vera20k`, fetched `origin/main` = `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`.
- Iron Harbor integration branch: `android-new` = `529cbe103361ee75b4862ebc0226ad143ebc4572` at the last audit.
- `android-new` is a descendant of the fetched upstream commit, seven fork commits ahead, zero behind. The workflow still checks upstream at run time and merges with `--no-ff`; conflicts stop the job without pushing.
- Iron Harbor legacy branch reference: `android-development` = `1293fb441727e2522f6a284d88faf4f108a1ee3e`, 104 Android source/test files differ from `android-new` in the compared paths. It is not safe to merge wholesale.
- Preserved backup: `backup/android-development-2026-11-27`.

## Changes carried on `android-new`

1. `302be2b45 feat(android): add movable touch control HUD` — upstream-neutral movable HUD implementation.
2. `a725dc756 ci(android): test launcher code on pull requests` — Android test workflow and tests.
3. `412226bcc ci(android): sync android-new with upstream after validation` — scheduled/manual validated upstream sync workflow.
4. `3eb6f6b6c ci(android): retry Gradle dependency throttling` — network-only Gradle retry policy (later refined).
5. `2e19b4453 fix(android): forward HUD keys without recycling` — public API-compatible key forwarding; early Kotlin/JVM checks; cache policy.
6. `d3f9e61be test(android): use Android API-compatible file helpers` — JVM test API compatibility.
7. `529cbe103 ci(android): upload debug APK artifact` — upload ARM64 debug APK after successful packaging.

## Relevant upstream work and open review

- Upstream Android HUD PR #5295: https://github.com/YuriPlanet/vera20k/pull/5295. The two HUD commits above are included in the branch base; avoid duplicating them.
- Iron Harbor internal PR #1: https://github.com/shietgrupe-cpu13/IronHarborMobile/pull/1. It carries the sync workflow to default `main`; currently draft/open at last check. Scheduled runs require the workflow on default branch.
- No external upstream PR has been opened by this task.

## Next comparison slices

- Controls: compare `TouchHudOverlay.kt`, `TouchHudLayout.kt`, `TouchHudKeyLatch.kt`, `TouchHudLayoutEditor.kt`, `TouchHudSettings.kt`, `VeraActivity.kt`, `ImmersiveFullscreenSettings.kt`, and `GameImmersiveMode.kt`.
- Launcher/storage: compare `LauncherActivity.kt`, `LauncherInstallation.kt`, `LauncherModCatalog.kt`, `LauncherModMapCatalog.kt`, `ModImportJob.kt`, `MapImportJob.kt`, `AssetImportJob.kt`, `SharedDataStorage.kt`, `LocalFolderPicker.kt`, diagnostics, and Rust configuration/asset-loading callers.
- Keep Iron Harbor branding, icon, and distribution policy separate from reusable upstream-neutral behavior.

## Controls audit findings

- Current `android-new` has no launcher settings/editor, per-button size/opacity, key remapping, multi-layout presets, or import/export for HUD layouts.
- Legacy `TouchHudSettings.kt` / `TouchHudLayoutEditor.kt` provide those UI/model functions. `VeraActivity.kt` handles pause/resume/focus key release, fullscreen, insets, and text input; these paths require adaptation and device testing.
- Legacy `src/app/android_input.rs`, `src/app/handler.rs`, and `android/src/lib.rs` implement a separate queued native HUD input/control-group route. Current `android-new` forwards keys through GameActivity's `onKeyDown`/`onKeyUp` to the existing native input queue. Do not combine both or add a second event loop.
- Old C1–C10 tap/hold assignment/recall behavior is engine-owned in the Rust control-group owner. Keep that ownership if migrating controls.
- The audit reviewed source/tests only; it did not run tests or establish physical-device behavior.
