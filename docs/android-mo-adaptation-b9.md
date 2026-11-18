# Android MO compatibility increment b9

Iron Harbor Mobile selectively adapts three behaviors identified in
[`928274/vera20k-MO`](https://github.com/928274/vera20k-MO), rather than merging
the separate executable fork wholesale:

1. Mental Omega's skirmish color priority skips the non-selectable flash color.
   The MO table is identified from its expected scheme count and sentinel
   colors; the stock Yuri's Revenge ordering remains unchanged.
2. Ares-enabled rules can use the fork's octagonal sight spread above retail's
   radius 10. The path is gated by loaded Ares rules and bounded to radius 256;
   vanilla rules retain their existing sight calculation.
3. A music track that fails playback preparation is marked unavailable in the
   process-local catalog. Repeat/global-repeat no longer pins automatic
   selection to that failed entry. Playable tracks keep their existing repeat
   behavior and source INIs are not modified.

Current VERA20K already tolerates incomplete AI-root references with
diagnostics when required registries are present. That MO-fork change was not
duplicated.

## Verification

- Four focused Rust regressions passed: MO color ordering, MO/vanilla color
  detection, Ares/vanilla sight behavior, and failed repeatable music selection.
- Optimized ARM64 Android library build passed with profile `shipping` and
  `phobos` enabled.
- Gradle `:app:testDebugUnitTest :app:assembleDebug` completed successfully;
  the Android JVM test task was up-to-date because launcher sources did not
  change in this increment.
- APK debug signature is valid; `zipalign -c -P 16 -v 4` passed; ELF `LOAD`
  segments use 16 KB alignment.
- Manifest reports package `org.ironharbor.mobile`, version `0.3.6`, target API
  36, and NativeActivity library name `vera20k_android`. APK includes the
  `arm64-v8a` library and no game-installation file extensions were found.
- No ADB device was connected, and no MO assets were available. Android 16
  device launch, Vulkan/audio behavior, and actual MO gameplay remain untested.

The APK is locally retained as
`out/apks/IronHarborMobile-0.3.6-android-arm64-debug-b9.apk`; build and
validation logs are retained in `out/logs/`. This is not a release candidate:
release signing was not configured. No FPS or other performance improvement is
claimed or measured.

Phobos support enables only the engine behavior VERA20K implements under its
Phobos feature. It does not load the original DLL. Full Mental Omega
compatibility remains unverified because large conversions can depend on DLL,
executable, or engine features not implemented in VERA20K. Remaining MO-fork
changes still require file-by-file review and compatible-asset validation.
