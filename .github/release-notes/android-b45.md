# Iron Harbor Mobile v0.3.6 — B45

Unofficial Android ARM64 fork of VERA20K. Yuri's Revenge game files are not included.

## What's new?

- **B43 — Launcher startup:** Applies launcher system-bar settings after `setContentView()` creates the window decor.
- **B44 — Game Activity startup:** Creates the NativeActivity decor before accessing its insets controller, with a legacy fullscreen fallback.
- **B45 — Game shutdown:** Returns normally from Rust `android_main` instead of calling `std::process::exit`, allowing `android-activity` to finish the NativeActivity and detach its JNI thread. The existing `VeraActivity.onDestroy()` process exit then runs after `super.onDestroy()`.

## Not fixed / still needs testing

- One Android 16 device session completed cleanly after entering the game and exiting to the launcher. Repeated launch/exit cycles and long-session stability still need testing.
- The captured session selected `mentalomega330mod` and passed launcher preflight, but does not establish full Mental Omega gameplay compatibility.
- Other Android versions and devices have not been tested with B45.

## What's verified?

- Optimized ARM64 Rust shipping build completed successfully; Gradle release APK assembled.
- All 46 Android JVM unit tests passed.
- APK signature, version metadata, ARM64 library, APK 16 KB ZIP alignment, and native ELF 16 KB LOAD alignment verified.
- Installed on Xiaomi 2412DPC0AG / Android 16 (API 36). B45 diagnostics show the game Activity entered foreground, audio initialized, and the launcher resumed after game Activity teardown. No destroyed-mutex abort appears in the B45 session logcat. Earlier B44 and startup failures remain in the archive's historical log buffer and are not B45 failures.