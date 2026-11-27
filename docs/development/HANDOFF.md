# Iron Harbor Mobile handoff

Updated 2026-11-27.

## Start here

Use `android-new` as the only integration branch. Upstream is `origin` (`YuriPlanet/vera20k`); the user's fork is `vera20k-fork` (`shietgrupe-cpu13/IronHarborMobile`). Do not switch to or merge `android-development` wholesale. The preserved read-only reference is `1293fb441727e2522f6a284d88faf4f108a1ee3e`; backup branch `backup/android-development-2026-11-27` remains intact.

No local Rust, Cargo, or Gradle compilation. `out/` is untracked user data and must be preserved. Use `GH_CONFIG_DIR=E:\DevTools\GitHubCLI`, `TMP/TEMP=E:\Temp` for GH CLI; do not print credentials.

## Current active run

- Run: [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106)
- Trigger commit: `529cbe103361ee75b4862ebc0226ad143ebc4572`
- Goal: check Kotlin/JVM test, ARM64 native build, debug APK assembly, then `Upload ARM64 debug APK` artifact step.
- Do not create another local build. If it fails, inspect `gh run view 45948571106 --log-failed`; fix the actual failure and push a focused commit.

## Next actions

1. Confirm run 45948571106 completes and has artifact `IronHarborMobile-android-new-arm64-529cbe103361ee75b4862ebc0226ad143ebc4572`; verify downloadable APK exists through GitHub Actions artifact listing.
2. Finish Phase 2: add APK metadata/ABI/signature/alignment checks and source/build metadata; prepare persistent signing support. Ask for approval only when repository signing secrets must be created or supplied.
3. Review internal draft PR #1. It adds the sync workflow to default `main`; schedule events will not run until it is merged. Existing workflow run already validates the file on `android-new`. Make it ready and merge internally if final diff and checks remain correct; do not open external upstream PRs.
4. Complete source-level Phase 3/4 audits from `android-development` SHA `1293fb441727e2522f6a284d88faf4f108a1ee3e`. Preserve gameplay/engine owners; migrate only focused Android changes.
5. Update all development docs with each verified commit/run and proceed into Phase 5 classification.

The controls audit has completed. First Phase 3 candidate: port a neutral `TouchHudSettings` model/persistence and safe preference migration with JVM tests; do not port the old branch's dedicated Rust input queue. Current GameActivity/winit routing is authoritative. Wait for the launcher/storage audit before selecting Phase 4 work.

## Architecture and compatibility facts

- `android-new` is at current upstream `main` `d1e28004aa17dc445cbfee450cfe6fa55c5fe949` plus seven commits; no current upstream commits are missing.
- Existing HUD code is `android/app/src/main/java/org/vera20k/TouchHudOverlay.kt`, `TouchHudLayout.kt`, with key forwarding in `VeraActivity.kt`.
- Latest Android package identity in upstream source is `org.vera20k`; min SDK 26, target/compile SDK 36, NDK `28.2.13676358`, ARM64 `.so` built with Cargo `fast` profile.
- Existing HUD: `TouchHudOverlay` forwards normal key edges; layout settings live in current `TouchHudLayout`. Legacy `TouchHudSettings` and editor have broader features but use an older input architecture and preference schema.
- Build workflow: `.github/workflows/sync-upstream-android.yml`; internal sync workflow PR #1 is `feature/enable-android-new-sync` → `main`.
- Device validation has not occurred. Keep a manual checklist for launch/resume, touch/HUD, SAF, mod/map import, and long-session stability.
