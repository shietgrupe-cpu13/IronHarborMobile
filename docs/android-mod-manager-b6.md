# Iron Harbor Mobile b6 — SAF Mod Folder Import

- Build date: 2026-11-18
- Source branch: `android-development`
- Application ID: `org.ironharbor.mobile`
- Version: `0.3.6` (`versionCode` 210)

## Three focused improvements

1. **SAF folder import.** The Mods page launches Android's Storage Access Framework folder picker and copies a selected folder tree into the app-private `files/Mods/<folder>` location. It preserves folder and file names, reads SAF content through `ContentResolver`, and does not interpret a `content://` URI as an engine filesystem path. The original user folder is left untouched.
2. **Collision-safe staged publication.** Each import copies into a temporary folder outside `Mods/`. The importer rejects unsafe provider-supplied names and an already-installed destination. Only after the full copy succeeds does it atomically move the staging directory into `Mods/`. Failed, cancelled, or conflicting imports clean up staging and leave existing mods unchanged.
3. **Progress and cancellation.** The launcher reports copied bytes, provides a cancel action, and shows success or a specific failure result. Cancellation is checked while traversing and copying files; the temporary tree is cleaned before the job finishes.

These are Android import plumbing changes only. The existing engine's profile and mod configuration paths from b5 remain the selection mechanism; the importer does not claim that every imported mod is compatible.

## Mental Omega fork comparison

The review of [`928274/vera20k-MO`](https://github.com/928274/vera20k-MO/tree/mo) and its [MO adaptation notes](https://github.com/928274/vera20k-MO/blob/mo/docs/MO-ADAPTATION.md) is documented in [b5 findings](android-mod-manager-b5.md). This build integrates no MO engine patch. It only makes it possible to import mod data through Android SAF. The VERA20K engine's existing `mo` archive/profile path remains the supported launcher option; this does not establish full Mental Omega compatibility or auto-detect a correct profile from imported files.

## Tests and results

- Android JVM suite: **14 tests passed, 0 failures**. This includes three new `ModImportJobTest` cases for nested-tree publication, collision preservation, and failed/cancelled cleanup. The suite does not exercise a live Android `DocumentsProvider`.
- Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`: passed.
- Debug and release APK signatures: verified with APK Signature Scheme v2. The release certificate SHA-256 remains `7440ad6a732f410f570b55b8d7e0138b0a9ab318ce5553afc02e8e0527ce1711`.
- Both APKs pass `zipalign -c -P 16 -v 4`; every ARM64 ELF `LOAD` segment has `0x4000` alignment.
- APK metadata: `org.ironharbor.mobile`, version `0.3.6`, versionCode `210`, minSdk `26`, targetSdk `36`, ABI `arm64-v8a`. The ARM64 native library is included. No MIX/YRM/MAP/MPR game files are bundled.
- `adb devices -l` found no connected device. Android 16 SAF interaction, actual import from a provider, launcher/engine configuration handoff, Vulkan startup, and game/mod launch remain unverified on hardware.
- No gameplay behavior or simulation performance changes were made; no performance claim or benchmark is made.

## Artifacts

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `IronHarborMobile-0.3.6-android-arm64-debug-b6.apk` | 68,399,960 bytes | `64BA433982E32E6D3DBF65767126E01BD02952E92377ABE115325FE96F70110E` |
| `IronHarborMobile-0.3.6-android-arm64-release-b6.apk` | 68,170,637 bytes | `65BEBE5ED0ECCD6CA0B803062A2959EB26AD238FDDFBF1643AE37977AF8B3768` |

APKs are saved under `out/apks/` and mirrored to `E:\Port Apks\VERA20K-ANDROID\`. Logs are under `out/logs/`:

- `android-mod-manager-b6-tests.log`
- `android-mod-manager-b6-gradle-and-apk.log`
- `android-mod-manager-b6-apk-verification.log`
- `android-mod-manager-b6-elf-16k.log`

## Known limitations

- The SAF picker flow has JVM coverage for the staging publisher but has not been run on an Android device or against OEM document providers.
- Imported data is app-private under `files/Mods`; it is not mirrored into `/storage/emulated/0/IronHarborMobile/Mods`.
- There is no engine-owned validation step for imported mod archives/rules and no automatic profile inference. Launcher selection remains a user choice, and the existing engine profile is applied when the game process next starts.
- Mod/profile changes cannot safely replace resources during an active match. Returning to the launcher and starting a fresh game process is required; Android app restart is not required when the game process has exited.
- Native engine configuration and archive loading were covered by the existing b5 Rust tests, not by this b6 Android device run. No device was connected.
