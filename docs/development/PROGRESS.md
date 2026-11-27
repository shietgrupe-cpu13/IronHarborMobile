# Iron Harbor Mobile progress

Updated 2026-11-27.

## Current Git state

- Working branch: `android-new` (remote `vera20k-fork/android-new`).
- Current branch HEAD: `529cbe103361ee75b4862ebc0226ad143ebc4572` (`ci(android): upload debug APK artifact`).
- Latest fetched upstream `origin/main`: `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`.
- Relationship: `android-new` contains upstream `main`, with seven fork commits on top and zero upstream commits missing at the last audit.
- Old mobile reference: `android-development` remote HEAD `1293fb441727e2522f6a284d88faf4f108a1ee3e`; backup branch `backup/android-development-2026-11-27` is preserved.
- Untracked `out/` is user/project data and has not been modified.

## Completed and verified

- `2e19b4453` removes the inaccessible `KeyEvent.recycle()` call from the HUD dispatch path and reorders CI so Kotlin/JVM checks run before Rust.
- `d3f9e61be` replaces Java 11 `Files.readString/writeString` calls in `AssetImportJobTest` with Android compile-stub-compatible helpers.
- GitHub Actions run [45946514319](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45946514319) succeeded: Kotlin/JVM validation, ARM64 Rust build (`fast`, 8m32s), and debug APK assembly (26s). It also saved Rust and Gradle cache entries.
- `529cbe103` adds pinned `actions/upload-artifact` upload for `android/app/build/outputs/apk/debug/*.apk`, named by branch and workflow SHA, with 14-day retention.

## In progress

- GitHub Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) is building the artifact-upload revision. At the last status check, Kotlin/JVM validation and environment setup passed; ARM64 Rust compilation was running. Confirm the APK upload and artifact metadata once it finishes.

## Known gaps

- No persistent signing credential is configured; generated debug keys differ between clean runners, so APK update/install-over-install is not yet guaranteed.
- No post-assembly APK identity, ABI, native-library, signature, ZIP alignment, or ELF alignment audit is in the workflow yet.
- No Android device test has been performed on this branch.
- The automatic upstream sync schedule only activates when the workflow is present on the default branch. Internal PR #1 is still draft/open; inspect and merge it after the artifact-upload workflow passes.
- No APK release is published.

## Controls audit (source-level only)

- Current `android-new` HUD: movable positions, show/hide, edit drag, `T`, C1–C10 key behavior; JVM tests cover position parsing/serialization and bounds. Current runtime path is GameActivity key down/up forwarded through `VeraActivity` to the existing native input route.
- Legacy branch adds settings/remapping/layout controls and a separate Rust-owned queued Android input/control-group path. Its source/tests were inspected but not executed; no legacy behavior should be called verified on `android-new`.
- Preserve user control groups in the engine owner. In the old implementation, tap/hold group behavior is delegated to Rust; do not duplicate this decision in Kotlin.
- Read-only audit agent report completed against exact SHAs in `UPSTREAM_TRACKING.md`; it made no source changes or builds.

See `HANDOFF.md` for exact next actions.
