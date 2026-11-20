# Iron Harbor Mobile B37

Unofficial Android fork of YuriPlanet VERA20K. This release does not include Yuri's Revenge game files.

## What's new

- The launcher writes an automatic diagnostic report to `Downloads/IronHarborMobile-startup-diagnostics.txt` on Android 10 and newer. It refreshes the same file on launch and when returning from the game, so it does not create a growing set of reports.
- The report includes app version, Android/device details, recent Android process-exit reasons and available traces, and a bounded logcat capture filtered to Iron Harbor Mobile's app UID. An uncaught Java crash adds its exception stack.
- The Diagnostics page has a button to share the latest startup report.
- Fixed the folder picker crash caused by passing a null row to Android's `ArrayAdapter`; parent-folder and empty-folder rows now use non-null labels.

## If the app crashes

Open your device's Downloads folder and send `IronHarborMobile-startup-diagnostics.txt` to the Iron Harbor Mobile developer. If the game crashes, reopen the launcher once so it can refresh the report with the previous process-exit details and available app logs. The report is limited to this app's UID; it does not include full system or vendor logs.

## Not fixed / limitations

- Google Play Protect may still show its existing warning for sideloaded builds; this release does not change the app's storage permissions or Play Protect classification.
- The Android report cannot guarantee that every native or vendor crash trace is made available by the device.
- Full mod import and gameplay compatibility were not validated as part of this crash-fix build.

## What's verified

- Android launcher unit tests pass, including folder-picker row regression tests.
- Release APK builds and verifies with the existing release signing certificate.
- Package `org.ironharbor.mobile`, version `0.3.6`, version code `244`; ARM64 native library included; APK ZIP alignment passes the 16 KB check; no game archives are bundled.
- Installed and launched on a Xiaomi 2412DPC0AG running Android 16. The report appeared in Downloads and was overwritten on relaunch with only one matching file present.
- The folder picker rendered its parent and directory rows on-device. Full mod selection/import still needs user testing.

## Build artifact

`IronHarborMobile-0.3.6-b37-release-arm64.apk`
