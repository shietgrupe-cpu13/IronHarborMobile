# Iron Harbor Mobile progress

Updated 2026-11-27. Current authoritative plan: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## Repository state

- Branch: `android-new`, tracking `vera20k-fork/android-new`.
- Current HEAD: `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e` (`docs(android): add autonomous Iron Harbor master plan`).
- Fetched `origin/main`: `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`; current branch is 11 commits ahead and 0 behind at this fetch.
- Legacy source reference: `android-development` at `1293fb441727e2522f6a284d88faf4f108a1ee3e`; its backup refs remain preserved. Do not merge the branch wholesale.
- `out/` is untracked user/project data and has not been read, changed or deleted.
- Current storage work is **uncommitted** in `android/` and `src/util/config.rs`. Preserve it; do not call it CI-verified yet.

## Completed and verified previously

- HUD key dispatch no longer calls inaccessible `KeyEvent.recycle()` (`2e19b4453`); the fix and API compatibility test passed in GitHub Actions.
- Android JVM file helpers were corrected for Android API compatibility (`d3f9e61be`).
- ARM64 debug APK artifact upload was added (`529cbe103`).
- GitHub Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) passed Kotlin/JVM validation, ARM64 Rust `fast` build, debug APK packaging and artifact upload. It produced a 53,330,817-byte artifact with 14-day retention. This is an older artifact and does not contain the current local storage changes.
- Master plan added remotely at `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e`; local branch was fast-forwarded without losing source changes.

## Current implementation in progress (not yet built or tested)

- `AndroidSharedStoragePath.kt`: safely resolves primary-volume SAF document IDs to real directories, selects `Documents/Ra2Harbor`, and reuses existing `RA2Harbor`, `IronHarborRA2YR`, or `RA2IRONHARBOR` roots.
- `VeraActivity.kt` and `AndroidManifest.xml`: informed opt-in to `MANAGE_EXTERNAL_STORAGE`, direct folder selection/validation and settings recovery; SAF copy remains available; direct data-root/config selection is persisted.
- `AssetImportJob.kt`: atomic config publication under the engine's Android `Config` directory (while preserving a pre-existing root config) and atomic data-root pointer helpers.
- `android/src/lib.rs` and `src/util/config.rs`: game install validation through the engine resolver; Android startup reads the selected data-root pointer; Android config search includes `Documents/<root>/Config/config.toml`; config updates retain prior config where available.
- JVM tests cover primary path safety, existing root reuse, required archive names, direct no-copy selection, config placement, pointer lifecycle and atomic publication. A focused Rust config-path test is added, and the Actions workflow runs it after JVM checks but before NDK/native compilation. These have **not** run locally or in Actions yet.

## Known gaps and gates

- The original Iron Harbor launcher GUI, launcher-based mod/profile/map management, diagnostics and renderer setting UI have not yet been restored on `android-new`.
- Private app data migration is not implemented; current changes preserve existing files and config text where the resolver can read it but do not yet migrate old private saves/maps/mods or `VERA.INI` into the shared root.
- Copy fallback without All files access must use private app storage for engine-writable state; this limitation needs clear UI and a SAF-backed shared-data design before claiming the master plan's shared-data requirement is complete.
- Current Android engine/docs do not support GLES; the legacy renderer selector is not safe to expose as working.
- Persistent release signing, full APK metadata/ABI/alignment checks, nightly release flow and the internal sync PR review remain open.
- Physical gameplay validation is incomplete. User report says game archives failed with `EACCES` and no game installation configured; exact device/API/artifact and failing path were not captured.

Next: complete source review of the uncommitted storage/config integration, run `git diff --check` and formatting-only checks; update docs; commit/push the focused slice; use GitHub Actions, inspect logs and artifact, then continue restoring the original launcher GUI. No local compilation.
