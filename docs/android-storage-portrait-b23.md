# Iron Harbor Android b23 — game source, Documents storage, portrait launcher

## Focused changes

1. **Keep the selected game folder in place.** The old flow copied the full
   selected tree into private staging and then made a second full copy at
   `Documents/RA2IRONHARBOR/Game`. The launcher now stores a persistable SAF
   grant and keeps the selected source untouched. The engine still needs a
   normal filesystem path, so one private copy is prepared and synchronized
   from the source before Play. No all-files permission is added. If the
   provider refuses persistent access, the copied installation remains usable
   as a snapshot and the launcher says later source edits will not sync.
2. **Use `Documents/IronHarborRA2YR` for user data.** The launcher creates
   `Mods`, `Maps`, `Saves` and `Logs` there; it no longer creates a second
   `Game` copy. When the prior app-owned `RA2IRONHARBOR` directory is present,
   it attempts an in-place SAF rename. If the provider does not support rename,
   it continues using the old directory without deleting or overwriting it and
   displays the actual location.
3. **Allow a responsive portrait launcher.** The launcher no longer requests
   landscape orientation or suppresses orientation/size recreation, so its
   existing width-aware layout is recalculated after rotation. The NativeActivity
   remains landscape-first for gameplay.
4. **Guard task-icon loading.** The POCO logcat captured a `Resources.NotFoundException`
   while loading the task-description icon, followed by a game-process crash.
   The launcher now catches drawable-load runtime failures and leaves the
   Android task icon in place instead of crashing startup. The b23 package
   contains the current vector icon resource.

## Verification

- `:app:testDebugUnitTest`: **31 tests passed**, 0 failures, 0 errors.
- `:app:assembleDebug`: passed with the existing optimized ARM64 native library;
  no Rust source changed in this increment.
- APK package: `org.ironharbor.mobile`, version `0.3.6`, version code `227`,
  target SDK 36, ABI `arm64-v8a`.
- `apksigner verify`: passed with the local Android debug key.
- `zipalign -c -P 16 -v 4`: passed. The ARM64 ELF LOAD segments use `0x4000`
  alignment.
- The APK contains `lib/arm64-v8a/libvera20k_android.so`; no MIX, YRM, MAP,
  MPR, SHP or PAL game assets were found in the APK.
- Launcher manifest has no fixed orientation. The game activity remains
  `sensorLandscape`.
- Build log: `out/logs/b23-android-build.log`.
- APK SHA-256:
  `0189C42BFCD98D872A71D2608F6C48301CDC7DBDC93E822CCE0414721F51B98D`.

## Not yet verified / limitations

- b23 was **not installed** on the POCO. The b22 app was still running in the
  background while its `Documents/RA2IRONHARBOR/Game` tree measured about
  419 MB; installing would stop that import. The older device log contains the
  icon crash described above. Build validation is not device validation.
- SAF source retention, edit-and-resync, revoking either folder grant, the
  in-place legacy-directory rename, portrait layout, game launch and gameplay
  all still need device checks after the current import is finished.
- VERA20K's native asset loader uses filesystem paths. Android SAF URIs cannot
  be passed to it as paths, so b23 avoids the extra public Documents game copy
  but still needs one private engine-readable copy. A true zero-copy SAF engine
  would require changing the engine's asset I/O and memory-mapping path.
- A release APK was not produced; this build uses the debug signing identity.
- No FPS or performance improvement is claimed.

## Artifact

`out/apks/IronHarbor-v0.3.6-b23-arm64-debug.apk`
