# Iron Harbor Mobile handoff

Updated 2026-11-28. The authoritative specification is [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## Repository and latest validation

- Repository: `shietgrupe-cpu13/IronHarborMobile`; branch: `android-new`; local `HEAD` and `vera20k-fork/android-new`: `8e02d79b80eedca143c1c900bd5a7eb45fb83675`.
- Preserve backup and `android-development` refs. The only untracked path is `out/`; do not inspect, stage, delete, or alter it.
- Actions [46053869832](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832) succeeded: Kotlin/JVM tests, focused Rust regressions, optimized ARM64 `fast` library, Gradle debug APK, persistent signing, identity/signature, ZIP/ELF alignment and artifact upload all passed. No device test occurred.
- Latest debug APK artifact: [download from Actions](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832/artifacts/15226298591). Name `IronHarborMobile-android-new-arm64-57c066a9b119629bcdfcba71ff3c88eb7aa14829`; 54,084,031 bytes; artifact digest `d8c6b01a1aacb573eea7aae617804edf8e27fbd915afabdd6e09da73a089a896`; expires 2026-12-11. Package/signature/alignment checks passed.
- Upstream `origin/main` was fetched at `068e965efcf27bfccc59fab3663fe7bded3788be`; upstream `ec197f8a9` was documentation/contract only. Run 46046233771 did not merge newer upstream changes.
- Commit `8e02d79b8` adds individual HUD button opacity through the shared layout editor and advances HUD export/import to schema v4 while retaining v1-v3 decoding. Its hosted checks passed; actual editor usability, import/update persistence and touch behavior still need device verification.
- Commit `57c066a9b` adds renaming of existing controls and v5 backup support, retaining v1-v4 decoding. CI passed; device verification is pending.

## Current implementation status

- Commit `1703630f7` integrates the configured action list and persisted launcher HUD layout with `TouchHudOverlay`. Existing positions, size/global scale, global opacity, visibility, set selection and saved layout editor are consumed by gameplay. EDIT toggles drag mode; EDIT long-press opens the shared layout editor. Held route keys release on pause, focus loss and destruction. Focused JVM tests passed.
- The HUD remains partial. Per-button opacity is implemented in `8e02d79b8` and CI-verified, but awaits device testing. Arbitrary add/duplicate/rename, named profiles, broader modifier/chord editing and physical multitouch testing remain.
- Red Resurrection catalog inference, nested-map path resolution, HUD backup migration, and HUD action/layout parser changes have host-side regressions. They do not prove actual RR or MO gameplay/resource selection on device.
- The reported 26,312 ms Play preflight was traced to duplicate archive indexing and source changes defer it, but equivalent device startup/mod/profile timings are still needed. No FPS/startup/memory improvement is measured or claimed.
- The cross-thread CheckJNI `DeleteGlobalRef` shutdown crash remains unresolved. Trace the actual JNI/audio thread owner; the partial HWUI destroyed-mutex tombstone is not enough to identify the cause.
- Source audit found no explicit Rust `DeleteGlobalRef`. Static `ACTIVITY` retains an `AndroidApp` clone for IME hooks and `ActivityAppGuard` clears it on `android_main` return; the pinned 0.6.0 API documents JNI attachment/context teardown at that boundary. This is a lifecycle risk, not a proven cause. The actual Thread-5 native backtrace or a reproducible CheckJNI shutdown trace is still needed.
- Launcher SAF copy fallback, full config migration validation and physical renderer/MO validation remain open. Preserve direct read-in-place game access, `Documents/RA2Harbor`, and current user data.

## Decisions and operating constraints

- Keep upstream VERA20K engine behavior authoritative; Iron Harbor is the Android frontend. Reuse the original launcher/storage implementations selectively. Do not duplicate the event loop or engine control-group semantics.
- Use GitHub Actions for all Rust, Gradle, tests and APK builds. Never compile locally.
- Never force-push, discard user changes, modify `android-development`, or touch untracked `out/`.
- No public release or external upstream PR is authorized without approval. Do not claim physical-device behavior until it is observed.

## Next actions

1. On POCO, install/update artifact `15222634738` without uninstalling; check HUD placement persistence, EDIT long-press editor, show/hide and resizing, action forwarding, C1/C2 tap/hold, route-key release, import/export, pause/resume and relaunch.
2. Continue the HUD editor gaps using existing settings/layout owners; make changes in coherent batches and validate on Actions.
3. Continue source-level trace of `DeleteGlobalRef`, launcher SAF fallback/config migration, and startup/mod/profile operations; require device evidence for runtime claims.
4. Validate MO/RR effective resources, renderer selection, game startup, map catalog, and vanilla transition on device when available.
5. Reconcile this file and the progress/roadmap/upstream/device documents after each verified Actions result.
