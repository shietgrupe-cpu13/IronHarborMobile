# Iron Harbor Mobile handoff

Updated 2026-11-27.

## Start here

Use `android-new` as the only integration branch. Upstream is `origin` (`YuriPlanet/vera20k`); the user's fork is `vera20k-fork` (`shietgrupe-cpu13/IronHarborMobile`). Do not switch to or merge `android-development` wholesale. The preserved read-only reference is `1293fb441727e2522f6a284d88faf4f108a1ee3e`; backup branch `backup/android-development-2026-11-27` remains intact.

No local Rust, Cargo, or Gradle compilation. `out/` is untracked user data and must be preserved. Use `GH_CONFIG_DIR=E:\DevTools\GitHubCLI`, `TMP/TEMP=E:\Temp` for GH CLI; do not print credentials.

## Latest build

- Run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) passed Kotlin/JVM tests, ARM64 native build, debug APK assembly, and upload.
- Downloadable artifact: `IronHarborMobile-android-new-arm64-529cbe103361ee75b4862ebc0226ad143ebc4572` (53,330,817 bytes; 14-day retention).
- Artifact source commit: `529cbe103361ee75b4862ebc0226ad143ebc4572`; the initial development-doc checkpoint was `c533ca3ebec18d245cd8215af92026074f5ba8bd`.
- At the time of the Actions run, no device testing had been reported. A subsequent user-reported device check is recorded below; do not attribute it to CI.

## Latest physical-device report

- User confirmed latest `android-new` APK installation, native engine startup, UI rendering, and visible HUD. Game startup fails with archive access denied (`os error 13`); screen says no game installation is configured. Device/API/artifact hash and active config/source path were not supplied. This is partial runtime validation only; do not claim gameplay readiness.
- Current SAF import copies the selected tree through `ContentResolver` to app-private staging and configures `ra2_dir` to that copy only after completion. It does not provide native runtime access to the chosen SAF URI.
- Legacy in-place access maps a primary-volume SAF ID to a raw path and depends on `MANAGE_EXTERNAL_STORAGE`; do not port that permission by default. Native asset loading currently enumerates directories and memory-maps path-opened archives, so a tree URI cannot be used as-is.
- Next Phase 4 action: use device diagnostics/config/path details to establish whether import was skipped/incomplete or an archive open is denied at the configured path; improve engine-owned launcher validation and recovery messaging; then evaluate a SAF-aware read source against optional user-granted broad access and the existing copy flow. Maintain the no-game-copy goal. No fix has been implemented or validated for this device report yet.

## Next actions

1. Finish Phase 2 APK package/ABI/signature/alignment validation and embed source/build metadata. Ask approval only when repository signing secrets must be created or supplied.
2. Review internal draft PR #1; scheduled sync requires the workflow on default `main`. The android-new run validated its content. Do not open external upstream PRs.
3. Implement the small Phase 3 HUD settings-model/persistence migration with tests. Do not copy the legacy Rust event queue; current GameActivity/winit owns input routing.
4. Use the completed Phase 4 audit for a launcher migration plan. Resolve the SAF/private-copy versus direct-path/`MANAGE_EXTERNAL_STORAGE` tradeoff before implementing file management.
5. Continue Phase 5 upstream classification and maintain these docs with each verified increment.

The controls audit has completed. First Phase 3 candidate: port a neutral `TouchHudSettings` model/persistence and safe preference migration with JVM tests; do not port the old branch's dedicated Rust input queue. Current GameActivity/winit routing is authoritative. Wait for the launcher/storage audit before selecting Phase 4 work.

## Architecture and compatibility facts

- `android-new` is at current upstream `main` `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`, plus seven functional commits and a docs checkpoint; no current upstream commits are missing.
- Existing HUD code is `android/app/src/main/java/org/vera20k/TouchHudOverlay.kt`, `TouchHudLayout.kt`, with key forwarding in `VeraActivity.kt`.
- Latest Android package identity in upstream source is `org.vera20k`; min SDK 26, target/compile SDK 36, NDK `28.2.13676358`, ARM64 `.so` built with Cargo `fast` profile.
- Existing HUD: `TouchHudOverlay` forwards normal key edges; layout settings live in current `TouchHudLayout`. Legacy `TouchHudSettings` and editor have broader features but use an older input architecture and preference schema.
- Build workflow: `.github/workflows/sync-upstream-android.yml`; internal sync workflow PR #1 is `feature/enable-android-new-sync` → `main`.
- Device validation has not occurred. Keep a manual checklist for launch/resume, touch/HUD, SAF, mod/map import, and long-session stability.
