# Android device test matrix

Updated 2026-11-28. User-reported results are identified separately from CI. Device model/API and artifact hashes are recorded only when supplied.

## Red Resurrection map catalog report (2026-11-28)

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| Xiaomi/POCO test device (exact model/API not newly captured) | ARM64 | User diagnostic, app build `0.4.3` code `208`; artifact SHA not captured in these logs | Red Resurrection selected. Launcher selection and lightweight Play preflight pass. `Resources/ClientDefinitions.ini` selects `INI\\MPMaps.ini`. The selected catalog has `[MultiMaps]` with nested map paths and per-map `GameModes` values, but no `[GameModes]` registry. `ra2.log` reports empty `MPModesMD.ini` rows at `2026-11-27T16:58:03Z`; in-game map/mode selection remains broken on this artifact. Source fix is pending Actions and a device retest. |

HUD layout export/import already existed in the launcher, but gameplay used a separate compact-layout preference file. Source changes now unify preferences and migrate old coordinates without deleting the old preference data. The new artifact still needs a device check: move HUD/Edit/C1/C10, export, update the app without uninstalling, verify placement remains, import the exported file, then relaunch gameplay and check the same positions and visibility. This has not been device-verified.

Actions run [46041308051](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46041308051) did not produce an APK: Kotlin unit-test compilation stopped on a missing `assertFalse` import. The source import was corrected; remote validation is pending.

## Latest launcher feedback (2026-11-27)

The user reports that deleting an old `config.toml` corrected the selected game
directory. This confirms a stale/competing config was involved in that device
case, but the deleted file's exact path and contents were not captured. Current
Android resolution prefers `Documents/RA2Harbor/Config/config.toml`, then the
root-level migration fallback, and only then prior app-private config. Keep the
fallback non-destructive; on the next device update verify the selected config
source and both paths before changing migration precedence.

The user also reports that the launcher has no usable renderer switch and no
dedicated Mental Omega profile. The current working tree adds both; no APK with
these changes has yet been built or device-tested. Required next device checks:
select Automatic, Vulkan and OpenGL ES one at a time; confirm the persisted
`VERA.INI` value and startup log's actual adapter/fallback. Then select MO and
check the Play preflight's effective `rulesmo.ini` / `artmo.ini` / `aimo.ini`,
MO archive family and Ares source against the actual selected MO folder before
starting a match. CI resource-fixture coverage is not a substitute for this.

## Latest supplied diagnostic (2026-11-27)

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| Xiaomi 2412DPC0AG / Android 16 (API 36) | ARM64 | User diagnostic, build `0.4.3` code `207`; no artifact SHA | `org.ironharbor.mobile.debug`; startup preflight succeeded (66 ms), selected game folder and shared config were visible, and vanilla YR INI roots loaded. First game process was explicitly killed by `VeraActivity.applyAppIconOnUi` while switching aliases. Source removal is in `4933e78d0`; run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed package checks on commit `ebc52b3fd`. The latest diagnostics build is being validated in [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713). Later shutdown abort remains unresolved: CheckJNI wrong-thread `JNIEnv*` during `DeleteGlobalRef`, with a captured HWUI destroyed-mutex tombstone. No fixed APK has been device-tested. |

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| GitHub Actions runner | Linux / Android package | Build-only; commit `ebc52b3fd` | Run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed Kotlin/JVM, focused Rust tests, ARM64 Rust `fast` build, APK assembly, package `org.ironharbor.mobile.debug` v0.4.3/code 207, v2 debug signature, ZIP alignment and ELF LOAD alignment `[16384, 16384, 16384, 16384]`. Artifact [15194923757](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660/artifacts/15194923757) uploaded; not device tested. |
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

Campaign/maps changes at `ebc52b3fd` passed run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) but have not been device-verified. The newer diagnostics build awaits run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713). The prior EACCES report remains unresolved until an exact artifact and path/permission details are tested.
