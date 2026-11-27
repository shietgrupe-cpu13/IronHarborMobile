# Iron Harbor Mobile progress

Updated 2026-11-27.

## Current Git state

- Working branch: `android-new` (remote `vera20k-fork/android-new`).
- Last recorded source-and-plan snapshot: `c533ca3ebec18d245cd8215af92026074f5ba8bd` (`docs(android): track autonomous mobile roadmap`); later documentation commits are non-build changes.
- Latest fetched upstream `origin/main`: `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`.
- Relationship: `android-new` contains upstream `main`, with seven fork commits on top and zero upstream commits missing at the last audit.
- Old mobile reference: `android-development` remote HEAD `1293fb441727e2522f6a284d88faf4f108a1ee3e`; backup branch `backup/android-development-2026-11-27` is preserved.
- Untracked `out/` is user/project data and has not been modified.

## Completed and verified

- `2e19b4453` removes the inaccessible `KeyEvent.recycle()` call from the HUD dispatch path and reorders CI so Kotlin/JVM checks run before Rust.
- `d3f9e61be` replaces Java 11 `Files.readString/writeString` calls in `AssetImportJobTest` with Android compile-stub-compatible helpers.
- GitHub Actions run [45946514319](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45946514319) succeeded: Kotlin/JVM validation, ARM64 Rust build (`fast`, 8m32s), and debug APK assembly (26s). It also saved Rust and Gradle cache entries.
- `529cbe103` adds pinned `actions/upload-artifact` upload for `android/app/build/outputs/apk/debug/*.apk`, named by branch and workflow SHA, with 14-day retention.
- GitHub Actions run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106) succeeded end to end: Kotlin/JVM checks, ARM64 Rust build, debug APK assembly, and artifact upload. Artifact `IronHarborMobile-android-new-arm64-529cbe103361ee75b4862ebc0226ad143ebc4572` is available, 53,330,817 bytes, with 14-day retention. Rust build took 5m12s on the cache-backed run; packaging took 11s.
- `c533ca3eb` adds the five persistent development and handoff documents. It was pushed without triggering a build.

## Latest successful build

- All workflow stages passed. `Push verified upstream merge` was skipped because no newer upstream commit was available.

## Known gaps

- No persistent signing credential is configured; generated debug keys differ between clean runners, so APK update/install-over-install is not yet guaranteed.
- No post-assembly APK identity, ABI, native-library, signature, ZIP alignment, or ELF alignment audit is in the workflow yet.
- One partial Android device test has been reported (installation/native startup/UI/HUD passed; game-file access failed). Full gameplay, device model/API, and artifact hash remain unverified; see `DEVICE_TEST_MATRIX.md`.
- The automatic upstream sync schedule only activates when the workflow is present on the default branch. Internal PR #1 is still draft/open; its content has now run successfully on `android-new` and can be reviewed for internal merge.
- No APK release is published; the APK is a downloadable workflow artifact, not a release.

## Controls audit (source-level only)

- Current `android-new` HUD: movable positions, show/hide, edit drag, `T`, and C1-C10 key behavior; JVM tests cover position parsing/serialization and bounds. Current runtime path is GameActivity key down/up forwarded through `VeraActivity` to the existing native input route.
- Legacy branch adds settings/remapping/layout controls and a separate Rust-owned queued Android input/control-group path. Its source/tests were inspected but not executed; no legacy behavior should be called verified on `android-new`.
- Preserve user control groups in the engine owner. In the old implementation, tap/hold group behavior is delegated to Rust; do not duplicate this decision in Kotlin.
- Read-only audit agent report completed against exact SHAs in `UPSTREAM_TRACKING.md`; it made no source changes or builds.

## Launcher and storage audit (source-level only)

- Legacy `LauncherActivity` validates the requested profile through Rust JNI `launcher_selection_config` / `validate_launcher_selection`; keep this shared resolver rather than adding a Kotlin INI parser.
- Legacy direct-path storage maps SAF trees to physical shared-storage paths and requires `MANAGE_EXTERNAL_STORAGE`. Current upstream instead uses SAF and copies into app-private staging. This is an unresolved design tradeoff against the prior read-in-place requirement; do not migrate broad storage permission blindly.
- Current upstream has no launcher screen/profile catalog. The old branch includes mod/map catalogs, import jobs, campaigns, startup diagnostics, and bounded log archives. Source existence alone is not runtime validation.
- Migration order: launcher/activity shell -> Rust-owned profile/config validation -> decide storage access model -> mod/map management -> diagnostics; defer campaign UI until current campaign progress writer and UX are confirmed.

## Physical-device storage result and follow-up

- User-reported physical-device check of the latest `android-new` APK: installation succeeded, native engine started, UI rendered, and HUD was visible. Gameplay startup failed with `Could not load the game archives: access was denied (os error 13)`; the screen also reported no game installation configured. Device model/API, artifact SHA, selected folder, import completion, active config path, and failing archive path were not included, so neither message's root cause is confirmed. This is a partial runtime result, not gameplay readiness.
- Current upstream SAF path is a copy importer: `VeraActivity.startFolderPicker` -> JNI `nativeOnFolderPicked` -> `startImport`/`ContentResolver` traversal -> `AssetImportJob` staging under app-private `filesDir` -> JNI configuration editor writes `[paths].ra2_dir` to the staged copy only after successful completion. The selected URI is not the runtime asset path.
- The old Iron Harbor path stored a physical shared-storage path and read in place, but `SharedDataStorage.directPathForTree` requires Android's `Environment.isExternalStorageManager()` and therefore `MANAGE_EXTERNAL_STORAGE`. That is broad access and only works for local primary-volume folders. It is not safe to transplant as the default.
- Engine file access currently requires filesystem paths: `AssetManager` enumerates the install directory and `MixArchive::load` opens/maps archive files. A SAF `content://` URI cannot be substituted directly. A persisted tree grant does not make raw native path reads valid.
- Next: obtain diagnostic details for the reported open failure; show the active configured path and validate it through the engine-owned asset/config resolver before enabling Play; present a direct recovery instruction for no configured installation; then compare a SAF-backed archive source against optional explicitly granted direct-path access and current copy import. Keep game-file read-in-place as the goal, avoid duplicating the installation, and do not request all-files access by default. No source fix is considered verified yet.

See `HANDOFF.md` for exact next actions.
