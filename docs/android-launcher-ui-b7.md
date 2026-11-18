# Iron Harbor Mobile b7 — Launcher and Touch UI Refresh

- Build date: 2026-11-18
- Source branch: `android-development`
- Application ID: `org.ironharbor.mobile`
- Version: `0.3.6` (`versionCode` 211)

## Three focused improvements

1. **Original launcher icon.** Replaced the placeholder shield monogram with generated Iron Harbor radar-and-port artwork, sized with transparent safe margins for Android's adaptive icon mask. Added a monochrome adaptive-icon layer. The artwork is original fork branding; it contains no EA or game logo.
2. **Launcher visual refresh.** Updated the palette to graphite, deep green, and signal green; added a stronger home hero treatment using the app icon; improved card spacing and touch-target sizing. Phone and landscape layouts still use the existing responsive max-width shell.
3. **Accurate touch and performance guidance.** The Touch page lists the gestures the Rust mapper currently handles and explains how to use VERA20K's persistent in-game Options > Interface > Edit Layout editor for existing UI parts and layout-added buttons. Diagnostics now points to the existing in-game Performance Stats panel, which reports frame/tick rate, CPU phases, per-core load, and GPU timing.

## RA2YRPORT button-customization comparison

The read-only RA2YRPORT reference has more than gestures: its `touch_bar.inc` supports a floating action overlay, action mapping, per-action position/size overrides (`RA2_TOUCH_POSITIONS`), and a bar scale setting. VERA20K's Android input mapper currently translates touch into the existing mouse/camera paths, and its layout editor moves existing interface elements. It does **not** have the reference's independent floating touch-action overlay or action remapping. This build does not invent launcher settings for those missing engine features. A later engine increment is needed to implement and test that overlay safely.

The existing VERA20K editor can move the interface and mod-provided layout buttons in a match: open Options > Interface > Edit Layout, drag an available part, then Save; Reset restores the game layout. This is not a claim of feature parity with RA2YRPORT.

## Tests and results

- Android JVM regression suite: **14 passed, 0 failures** through Gradle `:app:testDebugUnitTest`.
- Debug APK: `:app:assembleDebug` passed.
- Release APK: signed release packaging passed on retry. Release lint analysis stalled during the combined run under memory pressure and was excluded from the retry; b7 does not have a completed `lintVitalRelease` result.
- Debug and release signatures verify with APK Signature Scheme v2. Release certificate SHA-256 remains `7440ad6a732f410f570b55b8d7e0138b0a9ab318ce5553afc02e8e0527ce1711`.
- Both APKs pass `zipalign -c -P 16 -v 4`; packaged native ELF load segments use `0x4000` alignment. The ARM64 `libvera20k_android.so` is unchanged from b6.
- APK metadata: application ID `org.ironharbor.mobile`, version `0.3.6`, versionCode `211`, minSdk `26`, targetSdk `36`, ABI `arm64-v8a`. The generated icon resources are packaged. No MIX/YRM/MAP/MPR game files are bundled.
- Targeted Rust layout-editor/performance-panel tests were not completed in this increment because no Rust source changed; the Android JVM suite was run. The tests in these engine modules were not counted as b7 results.
- `adb devices -l` returned no connected devices. POCO/Android 16 launcher appearance, touch usability, Vulkan startup, SAF flow, and gameplay remain unverified on hardware.
- No gameplay or performance code changed; no FPS improvement is claimed and no Snapdragon 860 benchmark was collected. Input latency and memory under gameplay load still need on-device measurement.

## Artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `IronHarborMobile-0.3.6-android-arm64-debug-b7.apk` | 69,273,423 bytes | `39F0A087567636F99322C4CA5F39A015832638ACE3F552578BC5F06A0484EB69` |
| `IronHarborMobile-0.3.6-android-arm64-release-b7.apk` | 68,822,801 bytes | `32407FE04C2543237D1D7144C90E71F101348852777A82C129F2FF93FC450CD8` |

APKs are in `out/apks/` and mirrored to `E:\Port Apks\VERA20K-ANDROID\`. Gradle, signature/alignment, and ELF verification logs are in `out/logs/`.

## Remaining work

- Implement the RA2YRPORT-style floating touch-action overlay in the Rust renderer/input pipeline, with saved per-button positions, size, and safe action mapping. Do not claim this is done until the engine consumes and applies the settings.
- Connect a Snapdragon 860 device and collect the existing Performance Stats panel under an equivalent repeatable workload before considering optimization. Record CPU, render/GPU timing, frame pacing, memory, and touch latency where instrumentation permits.
- Run the full Android 16 device validation for launcher layout, custom controls, game import, startup/resume, and a vanilla match.
