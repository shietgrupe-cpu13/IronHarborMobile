# Iron Harbor Mobile v0.3.6 — B43

Unofficial Android ARM64 fork of VERA20K. Yuri's Revenge game files are not included.

## What's new?

- Fixed the Android 16 launcher startup crash reported on the Xiaomi 2412DPC0AG. `Window.insetsController` was accessed before `setContentView()` had created the window decor; the launcher now requests visible system bars after installing its root view.
- The launcher version label now includes the build number, for example `0.3.6 B43`.

## Not fixed

- B43 validation covered launcher startup only. Vulkan gameplay, mod loading, saves, touch controls, fullscreen transitions, and long-session stability were not retested.
- The previously reported destroyed-mutex shutdown crash and broad Mental Omega compatibility remain unresolved.

## What's verified?

- Android JVM tests: 46 passed, 0 failed.
- Signed release APK built as `org.ironharbor.mobile`, version `0.3.6`, version code `250`, ABI `arm64-v8a`.
- APK signature verification and 16 KB ZIP alignment checks passed.
- Installed over B42 on the connected Xiaomi 2412DPC0AG running Android 16. The app wrote a fresh application-launch diagnostic for version code 250, and its process remained alive after launch. Gameplay was not tested.
