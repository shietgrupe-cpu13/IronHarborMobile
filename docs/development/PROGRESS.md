# Iron Harbor Mobile progress

Updated 2026-11-27. Current authoritative plan: [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## Repository state

- Branch: `android-new`, tracking `vera20k-fork/android-new`.
- Current HEAD: `913e69cbfdd69d72f0abe9c3a8463ee188b95c4c` (`ci(android): fix focused config test filter`).
- Fetched `origin/main`: `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`; current branch is 11 commits ahead and 0 behind at this fetch.
- Legacy source reference: `android-development` at `1293fb441727e2522f6a284d88faf4f108a1ee3e`; its backup refs remain preserved. Do not merge the branch wholesale.
- `out/` is untracked user/project data and has not been read, changed or deleted.
- Current launcher/storage/engine integration is **uncommitted** across `android/`, `src/`, `.github/workflows/`, and `docs/`. Preserve it; do not call it CI-verified yet. The untracked `out/` directory remains untouched.

## Completed and verified previously

- HUD key dispatch no longer calls inaccessible `KeyEvent.recycle()` (`2e19b4453`); the fix and API compatibility test passed in GitHub Actions.
- Android JVM file helpers were corrected for Android API compatibility (`d3f9e61be`).
- ARM64 debug APK artifact upload was added (`529cbe103`).
- GitHub Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) passed Kotlin/JVM validation, ARM64 Rust `fast` build, debug APK packaging and artifact upload. It produced a 53,330,817-byte artifact with 14-day retention. This is an older artifact and does not contain the current local storage changes.
- Master plan added remotely at `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e`; local branch was fast-forwarded without losing source changes.

## Current implementation in progress (not yet built or tested)

- `AndroidSharedStoragePath.kt` restores `sharedDataRoot`, reuses established roots, and resolves supported primary-volume SAF IDs to paths. Direct game selection remains read-in-place.
- Game validation is profile-aware: RA2 requires `ra2.mix`; YR requires `ra2.mix` and `ra2md.mix`. Explicit base-RA2 detection is used when importing a new installation, and Rust updates the engine profile through its config writer.
- Launcher and engine configuration lookup now prefers `Config/config.toml`, then the data-root legacy `config.toml`; publication writes the canonical file without replacing the legacy file. Private-data migration no longer shadows an existing root-level config or `VERA.INI` by creating a canonical copy.
- Legacy JNI/storage integration was inspected on both Kotlin and Rust sides. Launcher JNI calls have matching Rust exports; the folder-picker/import JNI methods are registered against `VeraActivity` and its signatures match. The current game HUD routes keys through GameActivity's normal key callbacks rather than separate JNI.
- The old Ares support path was traced in `android-development`. Current Android startup, launcher preflight and asset-manager reconstruction now pass `Mods/AresSupport`; only `ares.mix` is considered, only for a selected mod, beneath that mod's own copy. Vanilla does not see the shared archive. A focused test covers vanilla isolation and mod precedence.
- The existing NativeActivity SAF copy importer remains in place. The new launcher direct-picker flow still cannot import a cloud/provider tree as a fallback; it reports that a local folder is required. Do not describe launcher SAF fallback as working until wired to the importer.
- The regression workflow now targets the actual config test name and includes focused nested-mod and Ares tests, after Kotlin/JVM validation and before NDK build. No local compiler/test was run; the Actions workflow has not yet validated this candidate.

## Known gaps and gates

- The restored launcher UI and storage flows are still uncommitted and have not passed Kotlin/JVM or APK CI. Physical-device behavior is not verified.
- Launcher SAF copy fallback for cloud/non-path providers is not connected. The legacy GameActivity importer remains, but the main launcher cannot yet route a new user into it without starting gameplay.
- A stale `CameraZoomSettingsService.nativeApplyCameraZoomLimits` declaration has no Rust export in the current branch, is not referenced by the launcher and is not registered in the manifest. It remains dormant; do not expose this as a working setting until its engine integration is migrated and validated.
- Private app data migration is non-destructive and copies only missing entries, but its behavior needs JVM coverage; no old source is removed. Config and VERA.INI migration now skips copying whenever either canonical or root-level shared files already exist.
- Current Android engine/docs do not support GLES; the legacy renderer selector is not safe to expose as working.
- Persistent release signing, full APK metadata/ABI/alignment checks, nightly release flow and the internal sync PR review remain open.
- Physical gameplay validation is incomplete. User report says game archives failed with `EACCES` and no game installation configured; exact device/API/artifact and failing path were not captured.

Next: finish source review of the integrated Kotlin/Rust storage changes and JNI inventory; run only `git diff --check`/static inspection locally; commit and push the coherent source slice; monitor Actions and inspect its logs/artifact. Continue with the launcher SAF fallback and remaining master-plan work after CI. No local Rust, Cargo, Gradle, or APK build/test.
