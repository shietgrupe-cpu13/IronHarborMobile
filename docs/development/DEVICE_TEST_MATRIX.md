# Android device test matrix

Updated 2026-11-27. Runtime results below were reported by the user; device model, Android API level, and installed artifact hash were not supplied.

| Device/API | ABI | Status | Checks |
|---|---|---|---|
| CI runner | Linux host / Android package | Build-only | Kotlin/JVM tests, Rust `aarch64-linux-android`, debug APK assembly, artifact upload. Not an Android runtime test. |
| User device (model/API not supplied) | ARM64 (reported) | Partial runtime test | Latest `android-new` APK installed; native engine started, UI rendered, HUD visible. Gameplay startup failed: `Could not load the game archives: access was denied (os error 13)`; screen also reported no game installation configured. Game loading and gameplay are not verified. |
| Android 12 (API 31) | ARM64 | Awaiting device test | Install, launch, HUD key dispatch, touch lifecycle, NativeActivity resume. |
| Android 13 (API 33) | ARM64 | Awaiting device test | Immersive fullscreen and gesture/three-button navigation. |
| Android 14–15 (API 34–35) | ARM64 | Awaiting device test | Storage Access Framework and file access, Vulkan surface lifecycle, import. |
| Android 16 (API 36), POCO/HyperOS | ARM64 | Awaiting device test | Install/update signature, immersive mode, Vulkan, landscape/insets, touch, audio, launch/resume, skirmish, mod/map files. |

## Manual sequence

1. Install the artifact and confirm its app ID/version. Verify update installation only after a stable signing identity is configured.
2. Launch/return/resume repeatedly; inspect startup logs and system bars.
3. Exercise HUD tap/hold, edit/move/persist layout, control groups, keyboard modifier chords, and multi-touch gestures.
4. Select game/mod folders through SAF; revoke URI access and confirm a clear error/recovery flow.
5. Launch vanilla and one mod, enumerate maps, load a skirmish, select/move a unit, and check audio.
6. Run a sustained match and collect logs/thermal/frame pacing only if diagnostics expose those measurements.

Record device model, Android build, app artifact SHA-256, exact steps, logs, and observed result before marking any item passed.

## Storage investigation

- Current `android-new` uses the upstream SAF import flow. `VeraActivity.startFolderPicker()` launches `ACTION_OPEN_DOCUMENT_TREE`; `nativeOnFolderPicked` starts the Java-side import; `startImport()` reads each document through `ContentResolver`, stages a full copy below `filesDir`, and `AssetImportJob.run()` publishes it before writing `config.toml` to that copied path. The SAF grant is needed during the copy; no game-tree URI is retained for runtime archive reads.
- The upstream manifest requests no broad storage permission. This avoids unrestricted storage access but duplicates the selected installation and depends on the import finishing successfully.
- The legacy `android-development` implementation reads a chosen primary shared-storage folder in place by translating a primary-volume SAF document ID to a filesystem path. Its `SharedDataStorage.directPathForTree()` explicitly requires `Environment.isExternalStorageManager()` and the manifest declares `MANAGE_EXTERNAL_STORAGE`. This avoids copying but is a broad permission and is not a general SAF solution (cloud/document providers do not map to a local path).
- The engine's archive loader is path-based: `AssetManager` enumerates with `read_dir`, then `MixArchive::load` uses `File::open` and memory mapping. Passing a `content://` URI as a `Path` cannot work. Persisting SAF access alone does not grant Rust raw-path access.
- The two reported startup symptoms do not yet establish a single root cause. A missing configured installation is consistent with an import/configuration that was never completed; `EACCES` indicates an attempted filesystem open was denied. The device config path, selected source, import outcome, failing archive path, and log excerpt are needed to connect them. Do not claim the SAF import itself is fixed or broken from these messages alone.
- Next functional-access milestone: first make the launcher show the configured `ra2_dir` and validate it through the same engine resolver used for gameplay, including an actionable failure path. Then decide the storage backend from measurements and Android API behavior. Preserve read-in-place as the goal; evaluate a SAF-aware engine file source (directory enumeration plus seekable archive reads) against an explicit user-granted all-files mode and upstream private-copy import. Do not add `MANAGE_EXTERNAL_STORAGE` by default, and do not pass a SAF URI as a path.
