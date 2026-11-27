# Android device test matrix

Updated 2026-11-27. This is a manual verification checklist; no physical-device validation is currently recorded for `android-new`.

| Device/API | ABI | Status | Checks |
|---|---|---|---|
| CI runner | Linux host / Android package | Build-only | Kotlin/JVM tests, Rust `aarch64-linux-android`, debug APK assembly, artifact upload. Not an Android runtime test. |
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
