# Iron Harbor Mobile v0.3.6 — B49

Unofficial Android ARM64 fork of VERA20K. The game files are not included.

## What's new since B45

- **B46 — Red Alert 2 campaign profile:** Added a selectable RA2 profile and launcher campaign page with victory-based progress tracking. Applied explicit Mental Omega Ares Auto profile behavior.
- **B47 — RA2 archives:** Fixed neutral archive resolution so Red Alert 2 can use `neutral.mix` without requiring Yuri's Revenge `ntrlmd.mix`.
- **B48 — Logs, mod manifests, Waypoint:** Prevented diagnostic ZIPs from including earlier exports; bounded and moved exports outside the Logs source folder. Added shared custom/partial mod INI and archive resolution. Connected the floating Waypoint action to Yuri's Revenge planning mode.
- **B49 — Mod maps:** Discover loose maps recursively in the selected mod, preserve relative paths, show them grouped on the launcher Maps page, and list them in Skirmish → Choose Map with the existing Game Mode filter. Maps are read in place without copying.

## Not fixed / still needs testing

- Mental Omega menu BGM remains unresolved. VERA reads WAV/AUD themes; its inspected MO folder has `chaoticimpulse.wma`, an XNA client resource. Android audio initialization succeeded, but this WMA track is not supported by the current theme loader.
- No connected Android device was available for this build. Mod-map listing and in-game selection need device gameplay testing.

## What's verified

- Optimized Rust `shipping` ARM64 build completed successfully with Phobos enabled.
- Android JVM tests: 55 passed, 0 failed, including recursive mod-map catalog tests.
- Focused Rust map discovery tests: 13 passed, 0 failed. The final directory-containment guard compiled in the shipping build; the focused test binary preceded that guard.
- Signed release APK metadata, signature, ARM64 native library, 16 KB APK alignment, and 16 KB ELF LOAD alignment verified.
- No MIX archives or map files are bundled. Players provide their own legally obtained game files.
