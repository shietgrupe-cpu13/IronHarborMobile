# Iron Harbor Mobile v0.3.6 — B40 through B42

Unofficial Android ARM64 fork of VERA20K. This changelog covers changes since the current GitHub Latest release, B39. Yuri's Revenge game files are not included.

## What's new?

- **B40 — World profiling:** Added diagnostic-only timings for world-render setup, instance generation, and finalization. A sample from one Mental Omega session recorded these subphases, FPS, frame time, simulation TPS, and GPU time. It was not a controlled before/after comparison, so no performance improvement is claimed.
- **B41 — Worker-pool controls:** Added Android controls for the existing Rayon worker pool and included worker count in diagnostics. One captured run reported the automatic pool at eight workers; comparative results for Auto/2/4/6 workers have not been established.
- **B42 — Immersive gameplay:** Added a persistent, enabled-by-default fullscreen option for gameplay. Players can reveal system bars with Android's edge-swipe gesture. The launcher and file picker remain in normal UI mode. Launcher and touch-HUD content now account for system-bar and display-cutout insets.

## Not fixed / still needs testing

- The repeated `pthread_mutex_lock called on a destroyed mutex` shutdown crash remains unattributed and unfixed.
- Full Mental Omega and general mod compatibility is not established; mods requiring unsupported executable/DLL behavior or unimplemented rules, rosters, and effects still need validation with user-provided files.
- Render scale below 100% remains experimental because of reported visual alignment and missing-texture issues. No FPS gain is claimed.
- Android 13–16, HyperOS, gesture and three-button navigation, cutouts, Vulkan gameplay, fullscreen/HUD placement, and B42 behavior have not been device-tested in this build session.
- Rayon worker-count comparisons and equivalent-workload FPS/frame-time benchmarks remain outstanding.

## What's verified?

- B42 Android JVM suite: 46 tests passed, 0 failed.
- B42 debug APK packaging passed for `org.ironharbor.mobile`, version `0.3.6`, version code `249`, ARM64.
- B42 debug APK signature verification, APK 16 KB ZIP alignment, and native ELF LOAD segment alignment (`0x4000`) passed.
- The B42 debug APK includes the ARM64 native library and no game installation files.
- No Android device was connected. Physical gameplay and fullscreen behavior are unverified.
- A release build must use the existing signing identity; the B42 release artifact is still pending build and verification.
