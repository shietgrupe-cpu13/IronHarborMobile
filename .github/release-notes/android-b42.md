# Iron Harbor Mobile v0.3.6 — B27 through B42

Unofficial Android ARM64 fork of VERA20K. This changelog covers work after the GitHub B26 release. Yuri's Revenge game files are not included.

## What's new since B26?

- **B27–B30:** Refined game/mod folder selection and import diagnostics; added camera zoom bounds, experimental world render scale/FPS cap controls, and performance log sampling. Render scale below 100% remains experimental because users reported visual alignment and texture issues; no FPS gain is claimed.
- **B27–B28:** Added configurable in-game touch HUD control groups, keyboard/text entry, and selectable HUD sets. Control-group labels and actions were corrected to match engine slots.
- **B31–B33:** Consolidated launcher-managed saves, maps, mods, logs, and configuration under the selected Documents data folder; improved folder selection, archive checks, import errors, and immediate-child mod discovery. Existing user data is retained; Android's native engine may still require a filesystem-readable game/mod source.
- **B34–B36:** Updated launcher navigation, icons, typography, and branding; built optimized ARM64 shipping packages and enabled the existing Phobos feature.
- **B37:** Improved startup diagnostics and folder-picker handling.
- **B38:** Aligned floating HUD keyboard shortcuts with Yuri's Revenge defaults.
- **B39:** Fixed an Android 12/API 31 startup linker failure caused by a direct reference to the API 33-only `AMotionEvent_getActionButton` symbol. The fallback keeps minSdk 26.
- **B40:** Added diagnostic-only timing for world-render preparation subphases. Measurements are profiling data, not evidence of a performance improvement.
- **B41:** Added Android controls and diagnostics for selecting the existing Rayon worker-pool size. The captured run showed the automatic pool reporting eight workers; comparative 2/4/6-worker performance was not established.
- **B42:** Added an enabled-by-default, persistent immersive-fullscreen gameplay option. System bars can be temporarily revealed by edge swipe. The launcher and file picker remain in normal Android UI mode; fullscreen is applied to gameplay only. Insets and cutout safe areas are applied to the launcher and floating HUD.

## Not fixed / limitations

- Full Mental Omega or general mod compatibility is not established. Mods requiring unsupported executable/DLL behavior, roster rules, effects, or assets still need validation against user-provided files.
- The engine's native filesystem access may require a readable local source or mirror; SAF `content://` URIs are not ordinary filesystem paths.
- Render scale below 100% has reported visual defects and should be treated as experimental. No controlled before/after FPS result is available.
- Android 13–16, HyperOS, gesture and three-button navigation, display cutouts, Vulkan gameplay, and B42 fullscreen/HUD placement have not been device-tested in this build session.
- The recurring `pthread_mutex_lock called on a destroyed mutex` shutdown crash remains unattributed and unfixed.

## What's verified for B42?

- Android JVM tests: 46 passed, 0 failed.
- ARM64 debug APK packaging succeeded for `org.ironharbor.mobile`, version `0.3.6`, version code `249`.
- APK signature verification, APK 16 KB ZIP alignment, and native ELF LOAD segment alignment (`0x4000`) passed.
- The APK contains the ARM64 native library and no game installation files.
- No Android device was connected; physical device behavior remains unverified.

The B42 debug APK is signed with the development key. A release-signed APK requires the existing release signing credentials and must not be replaced with a different signing identity.
