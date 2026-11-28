# Android device test matrix

Updated 2026-11-28. User-reported results are identified separately from CI. Device model/API and artifact hashes are recorded only when supplied.

## Latest CI artifact — awaiting physical-device test

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| GitHub Actions run [46090355627](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627) | ARM64 | Build-only, source commit `f4ef8bbf1`; upstream merge `b94e433a9` | Kotlin/JVM, focused Rust map/mod regressions, ARM64 optimized library, signed debug APK, package/signature/alignment verification and upload passed. Artifact [15241902961](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627/artifacts/15241902961), 54,431,155 bytes, digest `e9e96beb5bc217792df712842c8fef3e197b89296f6c754263bbc1a1e40b0b15d`. Not installed or played on a device. |
| GitHub Actions run [46053869832](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832) | ARM64 | Build-only, source commit `57c066a9b` | Kotlin/JVM, focused Rust, ARM64 optimized library, signed debug APK, package/signature/alignment verification and artifact upload passed. Artifact [15226298591](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832/artifacts/15226298591), 54,084,031 bytes, digest `d8c6b01a1aacb573eea7aae617804edf8e27fbd915afabdd6e09da73a089a896`. Not installed or played on a device. |
| GitHub Actions run [46050368117](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117) | ARM64 | Build-only, source commit `8e02d79b8` | Kotlin/JVM, focused Rust, ARM64 optimized library, signed debug APK, package/signature/alignment verification and artifact upload passed. Artifact [15225361525](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117/artifacts/15225361525), 54,083,163 bytes, digest `aecb19d0f1b9c09f91973f5bd2d9ea6ec452d32a02545acd03fa5827b0bb8a0e`. Not installed or played on device. |
| GitHub Actions run [46046233771](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771) | ARM64 | Build-only, source commit `1703630f7` | Kotlin/JVM and focused Rust regressions passed; ARM64 optimized `fast` library and signed debug APK assembled. Package `org.ironharbor.mobile.debug`, version `0.4.3` code `208`; v2/v3 signature, persistent signer, ZIP alignment and 16 KB ELF LOAD alignment verified. Artifact [15222634738](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771/artifacts/15222634738), size 54,082,616 bytes, uploaded artifact digest `59d3e8d5e97947dbe79ddea60eae2f9ee7cde5504bd33235922c12ed782ae77f`. Not installed or played on a device. |
| GitHub Actions run [46042175052](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052) | ARM64 | Build-only, fork source `44f5ce7a1`, final branch after upstream merge `e1af36488` | Kotlin/JVM and 13 focused Rust tests passed; ARM64 optimized `fast` library and debug APK assembled; package `org.ironharbor.mobile.debug`, version `0.4.3` code `208`; v2/v3 signature verified; ZIP alignment and ELF LOAD alignments `[16384, 16384, 16384, 16384]`; artifact [15222025462](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052/artifacts/15222025462), uploaded ZIP SHA-256 `d3ceb9070b4283d0897a8e815e5fa0b1aac0c70cfb39d6bcc7775b4aa873cb1d`. Not installed or played on a device. |

Required manual checks for latest build: install/update without uninstalling; confirm mod nested maps appear in Choose Map; switch mods and unselect to verify only unchanged generated map copies disappear; confirm personal maps and original mod files remain; test map launching. Also confirm HUD/Edit positions and settings survive; enter the in-game editor with EDIT long-press; move/resize/show/hide controls and save/cancel; test action dispatch and control groups; verify held route key releases on pause/focus loss; test import/export and relaunch. Validate mod/profile resources, renderer, startup time and clean exit separately. No FPS, startup or memory measurements are available for this artifact.

Earlier artifact `15222025462` also requires game/mod verification: select Red Resurrection and confirm actual mode/map catalog rows appear (including nested map paths), launch a match, inspect requested versus effective mod/profile/archive source, switch back to vanilla, and test clean exit/relaunch. Also verify MO resources, renderer selection and long-session stability. No device measurements exist for total startup time, mod/profile switching, FPS, frame pacing or memory.

## Red Resurrection map catalog report (2026-11-28)

| Device / API | ABI | Status | Observed checks |
|---|---|---|---|
| Xiaomi/POCO test device (exact model/API not newly captured) | ARM64 | User diagnostic, app build `0.4.3` code `208`; artifact SHA not captured in those logs | The 2026-11-27 report shows Red Resurrection selected, lightweight Play preflight passing, `Resources/ClientDefinitions.ini` selecting `INI\\MPMaps.ini`, and an empty in-game mode/map list (`MPModesMD.ini` had no usable rows). Later shared resolver/catalog source changes passed focused regressions in Actions run [46046233771](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46046233771). The exact tested artifact was not installed on this device, so the player-visible map issue remains open pending retest. |

HUD layout export/import already existed in the launcher, but gameplay used a separate compact-layout preference file. Source changes now unify preferences and migrate old coordinates without deleting the old preference data. The new artifact still needs a device check: move HUD/Edit/C1/C10, export, update the app without uninstalling, verify placement remains, import the exported file, then relaunch gameplay and check the same positions and visibility. This has not been device-verified.

Actions run [46041308051](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46041308051) did not produce an APK: Kotlin unit-test compilation stopped on a missing `assertFalse` import. The source import was corrected and later successful builds superseded this failed run.

Run [46041837302](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46041837302) compiled tests but failed one v1-compatibility fixture because it retained v3-only state. The fixture was corrected; no APK was produced.

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
| Xiaomi 2412DPC0AG / Android 16 (API 36) | ARM64 | User diagnostic, build `0.4.3` code `207`; no artifact SHA | `org.ironharbor.mobile.debug`; startup preflight succeeded (66 ms), selected game folder and shared config were visible, and vanilla YR INI roots loaded. First game process was explicitly killed by `VeraActivity.applyAppIconOnUi` while switching aliases. Source removal is in `4933e78d0`; later runs validated subsequent APKs, but no fixed APK has been device-tested. Later shutdown abort remains unresolved: CheckJNI wrong-thread `JNIEnv*` during `DeleteGlobalRef`, with a captured HWUI destroyed-mutex tombstone. |

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

Campaign/maps changes at `ebc52b3fd` passed run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) but have not been device-verified. The diagnostics change was included in later validated builds; run `45995480713` is historical. The prior EACCES report remains unresolved until an exact artifact and path/permission details are tested.
