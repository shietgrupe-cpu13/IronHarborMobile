# Iron Harbor Mobile / VERA20K upstream gap analysis

**Audit date:** 2026-11-27  
**Iron Harbor comparison revision:** `1293fb441727e2522f6a284d88faf4f108a1ee3e` (B60, remote `android-development`)  
**Upstream comparison revision:** `d1e28004aa17dc445cbfee450cfe6fa55c5fe949` (`YuriPlanet/vera20k` `main`)  
**Common ancestor:** `a7edf68c5929a04da6c4b4b220a42e77aefad37a`

This is a source/history audit, not a test report. No Rust, Cargo, Gradle, device,
or APK validation was performed. “Present” means the pinned source contains the
described path; it does not mean the user-visible flow has been exercised.

## Scope and history

`git rev-list --left-right --count B60...upstream-main` at the pinned revisions
reported 84 Iron Harbor-only and 413 upstream-only commits since the merge base.
That is a divergent-history count, not “the fork is 413 commits behind” in the
usual linear sense. The fork includes a large engine history, Android-specific
work, and local integration commits. The current upstream Android footprint is
smaller: its `android/` tree contains the Rust Android crate, one Gradle app,
manifest, `AssetImportJob.kt`, and `VeraActivity.kt`; text input also adds
`src/util/android_ime.rs` and engine/UI changes. Iron Harbor adds a separate
launcher, storage/catalog/import services, diagnostics, HUD, and corresponding
tests.

The comparison also identified upstream PR [#5256](https://github.com/YuriPlanet/vera20k/pull/5256),
head `b39ae43f518e30601ff3719caa4cd1908384e14c`. It was open at audit time; hrusten
said Android APKs were intended from v0.4.3 onward. Do not describe the PR as
merged. Its workflow is a useful baseline, but not the whole contribution path.

## Feature comparison

| Area | Iron Harbor B60 source | Upstream at `d1e28004aa17dc445cbfee450cfe6fa55c5fe949` | Audit result and upstream opportunity |
|---|---|---|---|
| Native Android game | ARM64 Gradle + Rust cdylib; existing winit event loop remains the game owner. `VeraActivity` is `NativeActivity` in a dedicated `:game` process; `LauncherActivity` is a separate process. | Android crate hands into the existing engine entry point. `VeraActivity` is `GameActivity` 2.0.2; upstream added platform IME hooks. | Preserve the NativeActivity/HUD lifecycle as a fork architecture decision; assess GameActivity integration only as a specific IME/lifecycle migration, not a class swap. |
| Launcher | Standalone native Android Views launcher with Home, Files, Mods, Maps, Settings, Touch, and Diagnostics sections. | Game's Rust shell/menu and the Mods page; no equivalent standalone Kotlin library/installation launcher. | Iron Harbor's navigation and branded presentation are fork-specific. Small, engine-neutral service APIs can be separated from it. |
| Game selection/storage | `LauncherActivity` invokes the SAF tree picker, resolves local shared-storage trees to a filesystem path, validates in place and writes engine config. `SharedDataStorage` routes generated data to `Documents/RA2Harbor`. | Upstream `VeraActivity` uses SAF and `ContentResolver` to copy the chosen retail folder to app-private staging and atomically publish it. | Both have user-facing SAF flows but distinct storage contracts. Iron Harbor direct paths require a special permission (see finding below); upstream's app-private copy avoids that permission but duplicates large game data. A provider/VFS abstraction is a separate architectural project. |
| Mod discovery | `LauncherModCatalog` reads active config and scans shared `Mods`; selection can include nested relative folders. External local folders are represented separately. | Upstream Mods page gained its own folder actions and mod shortcuts in `84ff1d6c146fb602585131b7b49d44c81b2a7cb1` (#5220), implemented in the engine UI and Android activity. | Complementary but not drop-in equivalent. Iron Harbor has launcher-side catalog ergonomics; upstream has in-game mod actions. Avoid porting the page wholesale. |
| Mod/profile selection | UI offers RA2, YR, MO, Auto, and Custom status. JNI calls engine-side selection/preflight functions; config is atomically saved for next launch. One shared active config is used, not independent complete config documents per mod. Custom profile roots cannot be edited in launcher. | Engine mod/resource loading and upstream Mods UI. No separate Iron Harbor-style Kotlin profile selector. | Valuable but requires a shared resolver contract so launcher and runtime cannot drift. The current Android enum still constrains some UI choices; document that `Custom` is not a complete launcher editor. |
| Maps | Imports maps into managed data; scans selected mod's nested map files without copying and presents active mod map catalog. | Upstream has general game map discovery and recent work around installed/editable maps, but not this launcher-managed per-mod catalog. | Useful mobile UX; storage source and duplicate/precedence semantics need upstream-neutral extraction. |
| Campaign progress | `LauncherCampaignProgress` has a parser/label helper but has no production caller at B60; `VeraActivity` consumes only an intent flag that asks the engine to open campaign selection. | Campaign progression belongs to engine/game menus; no equivalent external launcher progress UI identified. | There is no verified working launcher campaign progress screen in this snapshot. Do not advertise the parser as an active feature. Preserve engine save/progression ownership. |
| Ares / Phobos / mods | Android JNI resolves launcher selection against engine config/mod resource-selection code; shared Ares support is conditional and a mod-local `ares.mix` has precedence, per launcher text/source. | Upstream supports extension behavior from runtime game data, with recent Mods actions and `mod_actions.rs`. | Reuse upstream engine resolver. Do not submit Android-only hardcoded filenames or claim universal mod compatibility. |
| HUD layout | `TouchHudSettings` persists visibility, normalized positions, sizes, scale/opacity, layouts/docks, remappable actions, and C1–C10 control-set choices in Android preferences; editor and overlay are Kotlin. | No comparable customizable touch HUD in current upstream Android footprint. Upstream does have generic keyboard/hotkey and engine input systems. | Strong net-new accessibility candidate after cleanup and an API-neutral control model. Must not carry Iron Harbor colors/logo or fork-specific visual assets. |
| Touch, keyboard, mouse | HUD dispatches Android key/control-group actions through JNI; native game Activity also supports external-control mode and game text input plumbing. Rust remains authoritative for gameplay actions. | `GameActivity` and Rust IME feature route soft-keyboard composition to existing text fields; upstream's general keyboard/mouse input is engine-owned. | Iron Harbor's overlay is additional; upstream text input is already implemented by #4350 and should not be duplicated. Adapt only stable Android input hooks missing upstream. |
| Camera / display | Zoom limits persist through engine `config.toml`, with a service/JNI path for live event-loop updates. Immersive fullscreen is scoped to game Activity; insets are passed to engine. | Upstream has display inset support, Android text input, and its own engine camera settings. No Iron Harbor-specific fullscreen preference or HUD-safe layout. | Candidate portability depends on engine setting already existing upstream; fork service/preferences are not automatically reusable. Keep display-space transforms single-owner. |
| Diagnostics / crash support | Startup diagnostic, logcat capture and bounded diagnostic archive helpers/tests; launcher preflight writes stage timing and selection summary. | Engine logging, panic reporting, and Android import error reporting, but no same launcher diagnostics ZIP pipeline identified. | Good operational UX after source-neutral, bounded archive API cleanup. Logs must not archive old ZIPs or include user game data. |
| Rendering/backend | B60 launcher stores a renderer choice and the Rust bridge reads it, but the Android manifest still declares Vulkan as a required device feature while launcher copy says Vulkan is optional. The same launcher marks internal render-scale/FPS settings as saved but not applied by the renderer. | Upstream's current Android guide calls its build Vulkan-only and says Vulkan fallback/capability support is a later decision; its manifest also requires Vulkan. It declares GLES 3 but does not describe it as a runtime fallback. | Iron Harbor's backend and scale/limit controls are not verified working settings in this snapshot. Neither source comparison nor prior FPS reports establish a performance improvement. |
| Build automation | `.github/workflows/android-arm64.yml` supports manual/automatic builds, caching, ARM64 Cargo NDK build, signing variants, JVM tests, package/ABI/signature/alignment checks, logs and artifact/release stages. | PR #5256 adds a 100-line manual/tag-triggered release workflow that installs SDK/NDK, Rust target and cargo-ndk, builds shipping profile and attaches APK to a GitHub Release. No Cargo cache, sccache, JVM tests or equivalent metadata/16-KiB verification in that PR's workflow. | Fork has useful CI hardening to offer as a follow-up after #5256 lands; do not submit a competing Android workflow. Validate current upstream's release policy and action versions first. |
| Dependencies | B60 retains Rust `android-activity`/winit native-activity integration, vendored notices, and custom Android UI. | #4350 adds `androidx.games:games-activity:2.0.2`, platform GameActivity glue, and changes the native bridge/engine IME path. | The activity migration is architectural, not a drop-in Gradle dependency. Preserve third-party license notices; compare dependency versions and AGP/NDK compatibility before adapting. |
| Code license and artwork | Fork keeps GPLv3 source and B60 Android license/attribution assets; docs identify its shield-and-monogram launcher icon as original Iron Harbor art. | Upstream `LEGAL.md` distinguishes GPLv3 code from VERA20K-created art under CC BY-NC-SA 4.0, retains Ares/Phobos notices, and says EA owns the game files and depicted game art. | GPLv3 does not grant rights to EA game assets or cover every art asset. First contributions should be code-only; preserve Ares/Phobos and dependency notices when carrying those portions. Verify every image's source/license before distribution. |

## Important storage and verification findings

1. **Permission/documentation mismatch:** B60's `AndroidManifest.xml` declares
   `MANAGE_EXTERNAL_STORAGE`; `LauncherActivity.renderFiles` checks
   `Environment.isExternalStorageManager()` and offers the system special-access
   settings page. Its documented “read in place” source therefore depends on
   broad shared-storage access on Android 11+. This conflicts with an older
   paragraph in `docs/android.md` saying no all-files permission is requested.
   The source is authoritative for this audit. This permission is a major
   distribution/review concern and should not be proposed to upstream as a
   default solution.
2. **Selected local source versus SAF provider:** `importGame` and `importMod`
   accept only a selected tree that can be resolved to a real local `File`;
   cloud/document providers that cannot be represented as normal paths are
   rejected with a message. This is intentional for the current Rust filesystem
   interface, but limits the accessibility claim. The SAF picker itself does
   not make arbitrary `content://` URIs usable by Rust `std::fs`.
3. **Config authority:** the launcher calls Rust/JNI functions such as
   `nativeConfigForMod` and `nativeValidateCurrentSelection`, then publishes
   `Config/config.toml`; gameplay reads engine config on game-process startup.
   That is stronger than duplicating INI filename guesses in Kotlin. Still,
   settings are generally “next launch” and the single config selects one
   profile/mod at a time; it is not a multi-profile snapshot store.
4. **Runtime status:** test sources exist for storage/path resolution, catalogs,
   imports, HUD settings, launch gating, diagnostics, and fullscreen. Their
   existence does not show they passed on the pinned revision. No test or
   device run was performed for this audit.
5. **Performance evidence:** code changes and user reports do not prove an FPS
   gain. In particular, a user-observed expensive “driver handoff” phase should
   be interpreted as render submission/wait instrumentation, not a game command
   or proof that GPU execution itself is the bottleneck. Any contribution needs
   separate CPU encode/submit/wait, GPU timing, and frame-pacing measurements on
   equivalent scenes.
6. **Renderer capability mismatch:** B60's `LauncherActivity` presents an
   OpenGL selection, and its Rust bridge accepts `vulkan`/`opengl`, but the
   manifest declares `android.hardware.vulkan.level` required. The launcher
   diagnostic also states Vulkan is optional. Upstream
   `d1e28004aa17dc445cbfee450cfe6fa55c5fe949` documents
   Vulkan-only and marks fallback as a future decision. Treat GLES selection as
   experimental/unverified until package installability, `wgpu` adapter
   creation, renderer completeness and actual device behavior are tested.
7. **Inactive/unapplied launcher settings:** the B60 graphics page itself says
   internal render scale and FPS choices are saved but not applied by the
   renderer. They must be labeled experimental/unavailable in any upstream
   proposal until a production render-target/frame-limit owner and correctness
   path exist. This audit makes no FPS claim.
8. **Campaign helper is not an active screen:** an Android-source B60 reference
   search found no production call of `LauncherCampaignProgress.read` or
   `.label`; only `VeraActivity` reads the open-campaign-selection intent flag.
   A helper/test file is not evidence of working progress tracking.
9. **No binary/device audit:** this task did not inspect an APK, install on a
   device, run source tests, or profile a scene. The fork's guide records prior
   reports, but they are not independently reproduced here.

## Upstream changes with direct mobile relevance

- `21fa6d2f305c79fa210992885ea0845fd2c58ba7` (#4350), **Land Android text input
  with GameActivity**: moves upstream Android activity to GameActivity, adds
  IME-to-Rust text/composition support and updates affected chat/login/name
  screens. Iron Harbor B60's native activity/input architecture differs. This
  behavior already exists upstream; only a gap analysis/adaptation is needed.
- `84ff1d6c146fb602585131b7b49d44c81b2a7cb1` (#5220), **Give Android's Mods page
  its own folder actions and mod shortcuts**: extends in-game Mods UI, import
  flow and `util::mod_actions`. Complement the launcher; do not duplicate it.
- `1fd4e8f44a2bb314a43754e64bb371e6414ee822` (#5174): separates
  simulation/client-frame benchmark reporting and worker-count experiments.
  Useful as a measurement model, but not Android GPU profiling by itself.
- `44e9bd47322ff12aaa9b1317745a4283ea75c44f` (#5279): speeds preparation
  of presentation world data. Inspect current source and benchmark impact before
  considering Android relevance.
- `44c34ef7d8a12dbeabbb7f0185e25f3a2a5c5d96` and
  `469eac6a38fb620f9fdce0afa6b570fa202281f2`: parallel entity-store object
  turns and copy-on-write world fields, respectively. Both are broad engine
  architecture changes absent from B60; do not transplant into the Android
  fork wholesale. Evaluate Android ARM64 scheduling, memory and determinism on
  the current upstream owner first.
- `a349bca714cfc84844afa6e3cfbdacda1622ac59`: release PGO training on native
  retail gameplay. This is not an Android-trained profile and cannot substantiate
  an Android performance claim. An Android-specific training workflow would
  need equivalent ARM64 workloads and repeatable tooling.
- `2412c8c49dc97663cbaf55860974788bbc69e911`: sets named worker threads and an
  8 MiB stack. Android adoption needs memory/worker-count review, especially on
  memory-constrained devices; its presence is not evidence of an Android speedup.
- Android build PR #5256 head `b39ae43f518e30601ff3719caa4cd1908384e14c` is open
  at audit time. Upstream's `docs/android.md` reports an initial Android 16
  ARM64 device-verified port and lists open issues; this is upstream's reported
  status, not a device validation performed here.

## Licensing and assets

Both repositories retain GPLv3 notices. Any upstream code contribution must
retain applicable copyright headers/attribution and comply with upstream's
contribution guidance. Keep Iron Harbor name, logo, color scheme, promotional
content and release configuration out of a neutral PR. B60 vendors attribution
and license notices for dependencies/icons under Android assets; carry those
notices only when carrying the corresponding asset/dependency. Upstream's
Android-bundled `LEGAL.md` distinguishes code under GPLv3, VERA20K-created art
generally under CC BY-NC-SA 4.0, Ares and Phobos notices, and EA-owned game
assets/art. A public repository is not a license grant for EA art. Iron Harbor's
Android guide describes its app icon as original shield-and-monogram art; keep
that branding/assets out of upstream-neutral PRs. The audit did not independently
establish ownership/license for every image file.

## Classification summary

- **Ready in principle:** an upstream-neutral touch-control model/layout editor
  after extracting brand-free settings and proving Android lifecycle behavior;
  selected pieces of CI verification after PR #5256 is merged.
- **Requires cleanup:** launcher/mod selection boundary, diagnostics export API,
  and documentation consistency around permissions and storage.
- **Requires architectural adaptation:** direct SAF provider access, multi-mod
  config profiles, and HUD key dispatch against upstream GameActivity.
- **Requires device validation:** HUD overlays/multitouch, immersive mode,
  Android 12–16 IME/lifecycle, SAF providers, Vulkan/GL switching, and imports.
- **Already implemented upstream:** base Android native activity, SAF asset
  importer, Android IME through GameActivity, engine Mods folder actions, and
  Android workflow proposal (PR #5256, still open).
- **Iron Harbor-exclusive:** branded launcher, all-files-access direct path
  mode, separate `Documents/RA2Harbor` user-data convention, mod map browser,
  campaign progress convenience UI, floating HUD/layout editor, diagnostics
  archive UI, and fork release/signing policy.

## Source index

- [Iron Harbor B60 Android source](https://github.com/shietgrupe-cpu13/IronHarborMobile/tree/1293fb441727e2522f6a284d88faf4f108a1ee3e/android)
- [Iron Harbor launcher settings and renderer bridge](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/app/src/main/java/org/vera20k/LauncherActivity.kt)
- [Iron Harbor Android manifest](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/app/src/main/AndroidManifest.xml)
- [Iron Harbor engine/JNI Android integration](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/src/lib.rs)
- [Iron Harbor launcher catalog and SAF flows](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/app/src/main/java/org/vera20k/LauncherActivity.kt)
- [Iron Harbor campaign progress helper](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/app/src/main/java/org/vera20k/LauncherCampaignProgress.kt)
- [Iron Harbor Android guide at B60](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/docs/android.md)
- [Upstream Android guide at audit revision](https://github.com/YuriPlanet/vera20k/blob/d1e28004aa17dc445cbfee450cfe6fa55c5fe949/docs/android.md)
- [Upstream Android text input change](https://github.com/YuriPlanet/vera20k/commit/21fa6d2f305c79fa210992885ea0845fd2c58ba7)
- [Upstream Android Mods actions change](https://github.com/YuriPlanet/vera20k/commit/84ff1d6c146fb602585131b7b49d44c81b2a7cb1)
- [Upstream Android workflow PR](https://github.com/YuriPlanet/vera20k/pull/5256)
- [Upstream GPL license](https://github.com/YuriPlanet/vera20k/blob/d1e28004aa17dc445cbfee450cfe6fa55c5fe949/LICENSE-GPL)
- [Upstream legal, art and attribution terms](https://github.com/YuriPlanet/vera20k/blob/d1e28004aa17dc445cbfee450c5fe6fa55c5fe949/LEGAL.md)
- [Iron Harbor Android-bundled legal notices at B60](https://github.com/shietgrupe-cpu13/IronHarborMobile/blob/1293fb441727e2522f6a284d88faf4f108a1ee3e/android/app/src/main/assets/licenses/LEGAL.md)
