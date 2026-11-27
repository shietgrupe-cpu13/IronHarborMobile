# Android device test matrix

Updated 2026-11-27. User-reported results are identified separately from CI. Device model/API and artifact hashes were not supplied for the prior test.

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| GitHub Actions runner | Linux / Android package | Build-only, previous revision | Kotlin/JVM, ARM64 Rust `fast` build, debug APK assembly and artifact upload passed in run [45948571106](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45948571106). This predates the current storage/config changes. |
| User Android device (model/API unknown) | ARM64 (reported) | Partial user-reported run | APK installed; native startup, UI and HUD visible. Game startup failed: `Could not load the game archives: access was denied (os error 13)` and no game installation was configured. Exact artifact, selected path, permission state and failing archive are unknown. |
| Android 12 / API 31 | ARM64 | Awaiting test | Install/launch, folder selection, permission denied/revoked recovery, native asset access, resume. |
| Android 13–15 / API 33–35 | ARM64 | Awaiting test | SAF fallback, direct folder access, launcher/game transitions, Vulkan lifecycle and navigation modes. |
| Android 16 / API 36, POCO/HyperOS | ARM64 | Awaiting test | Reuse `Documents/Ra2Harbor`, game/mod/map access, renderer support, HUD input, long-session stability and diagnostics. |

## Storage test sequence after CI artifact is available

1. Record model, Android version/API, artifact SHA-256 and permission state.
2. With All files access declined, use SAF copy and confirm the explanation, copy progress, config selection and next-launch behavior.
3. Grant All files access explicitly; select an existing game folder on primary storage and confirm the game remains in place (no duplicate game folder).
4. Confirm existing `Documents/Ra2Harbor` is reused; if only a legacy `RA2Harbor`, `IronHarborRA2YR` or `RA2IRONHARBOR` exists, confirm it is reused without moving or deleting files.
5. Confirm restart retains game path and `Config/config.toml`; test mods/maps/saves and a readable required archive.
6. Revoke permission and verify a useful recovery message; restore permission and retry.
7. Launch vanilla, one mod, load a map, select/move a unit, and check audio. Record failures and logs verbatim.

No current uncommitted fix is device-verified. The prior EACCES report remains unresolved until an exact artifact and path/permission details are tested.
