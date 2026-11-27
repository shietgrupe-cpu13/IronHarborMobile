# Android upstream compatibility review: VERA20K v0.3.7–v0.4.0

Review base: Iron Harbor Mobile B49 (`aa77b7151`, Android branch `android-development`).
Upstream refs inspected: the complete tagged ranges v0.3.6→v0.3.7, v0.3.7→v0.3.8,
v0.3.8→v0.3.9, v0.3.9→v0.4.0, plus the subsequent first-parent changes through
`origin/main` at `eb05f451e965337534a68d631a2217a242271b36` (2026-11-22).
The four tagged ranges contain 138, 63, 316 and 106 commits, respectively; the
release-by-release table records the Android-relevant findings and disposition.

This is a selective port. The Android fork has substantial platform and gameplay
divergence; upstream changes were checked against the affected Android code before
porting. A change is marked “already present” only when the corresponding behavior
was found in this fork. Incompatible or broad changes are listed as deferred.

## Release-by-release audit

| Upstream release | Relevant upstream changes | B49 status and disposition |
|---|---|---|
| **v0.3.7** | Ares `[Basic] FillSilos=yes`; `UnitDelivery` waits its superweapon deferment; script-changed unit types use their new movement; plus campaign, movie, menu, online, and gameplay fixes. | **Ported:** Ares silo filling now follows the configured silo-storage behavior; stock rules still grant cash. Missing Tiberium is a safe no-op and zero-value rules cannot loop forever. **Already present:** `UnitDelivery` deferment and the relevant movement-type refresh behavior were checked in the fork. The Windows/browser and online UI changes were not relevant to Android's current architecture. |
| **v0.3.8** | Extracted-mod artwork/resources, production queue, convenience UI, Ctrl+D contextual orders, offline chat, and large-Ares trigger type lookups. | **Already present:** B49 has mod `[Files]`, `[Archives]`, `[Extensions]`, custom INI roots, Android mod discovery, maps, and mode/profile selection; these were verified in `src/util/mod_definition.rs`, `src/app/mods.rs`, and Android integration. **Ported:** Ares trigger techno-type lookups are indexed case-insensitively while retaining first-match and sentinel semantics; trigger evaluation borrows represented-house data instead of allocating a temporary vector. **Deferred:** broad desktop menus/chat interactions need an Android-specific UI design and input validation. |
| **v0.3.9** | Windows OpenGL fallback and selectable graphics API, graphics-device recovery, lower graphics memory use, plus damaged-input and gameplay fixes. | **Ported selectively:** compatible GPU resource lifetime fixes and the non-platform-specific allocation/borrow optimizations listed below. This continuation adds launcher-selectable wgpu OpenGL ES compatibility on Android: Automatic tries Vulkan then GLES if initialization fails; explicit Vulkan/GLES preferences are honored. This is a compatibility option, not a performance claim, and still needs device testing. Windows DX12 selection and device-loss recovery were not copied because their platform behavior does not directly transfer. |
| **v0.4.0** | `.map`/`.mpr` map discovery, classic RA2 rules/mod support, mod-aware random derelicts, track-following trains, map tunnels, high-refresh rendering, and online/skirmish UI changes. | **Already present in part:** B49 includes RA2 campaign/profile support, `.map`/`.mpr` handling and imported/recursive mod-map support; existing train/path data was checked. **Deferred:** the upstream rail/train implementation changes 27 files; native tunnel locomotion changes 73 files. They do not apply cleanly to this fork and overlap different pathing/tunnel behavior, so neither was blindly merged. High-refresh rendering also remains deferred until frame pacing and thermal behavior are measured on Android. |

## Relevant changes after v0.4.0

The B49 branch already contains several important upstream performance commits:
unchanged battlefield and in-game menu frames are retained (`23adb64ed`,
`25a318e96`), tactical effects advance with `Main_Tick` rather than display
refresh (`bb9eb7399`), radar pictures and posed voxel images are composed on the
GPU (`bb23a5838`, `149c77060`), and the radar ground-tile list is reused
(`a96c5fff0`). `git merge-base --is-ancestor` confirms these are in B49's
history; they are baseline behavior, not new Android ports. They have no
equivalent Android before/after benchmark in this workspace.

The subsequent main-branch history was reviewed through the fetched upstream head.
Compatible changes selected for this pass were: avoid rebuilding a heap-backed
movement snapshot for every mover; reuse movement/path work buffers; avoid repeated
movement-class scans in zone construction; borrow line-of-fire and projectile cells;
avoid building a sorted foundation set for a boolean gate check; and reduce
unnecessary GPU diagnostic/depth and shell-atlas resource retention. Optional GPU
timer setup now fails closed with a diagnostic message instead of preventing the
renderer from starting.

Later compatible changes added in this pass include worker-owned scratch for
parallel expiry-listener rereads instead of a thread-local `RefCell`; clone-to-
borrow conversions in terrain, AI, combat, production, jammers, shroud and
superweapons; and bounded allocations for INI counts that feed Ares/Phobos
vectors. Those bounds follow current upstream behavior and have regression tests.

The Android GLES follow-up reuses the engine's WebGL-compatible render passes and
offscreen presentation path. The launcher saves the selection in the existing
`[graphics]` configuration section, and NativeActivity reads it before wgpu
initialization. GLES uses downlevel limits and leaves the optional GPU timer
disabled. Actual GLES startup, presentation, gameplay, and driver behavior have
not been checked on hardware.

Adapted upstream source commits include `dc271ec6c` (path-zone construction),
`063f6a405` (render-target and shell-atlas lifetime), `e4f7e0ff7` (optional GPU
timer initialization), `b522c5827` (silo semantics), `0b175a8e6` (Ares trigger
lookup), `216cf16da` (movement allocation), `a56a30f76` (gate footprint check),
`7a3f96bb9` (line-of-fire cell borrowing), `e0974e6e4` (expiry-listener scratch),
`e9f23fd63` (temporary clone reductions), and `4d4ad3be5` (bounded rules counts).
Direct applicability checks for larger commits `ad27337eb`, `720377d72`,
`9aad0c6f0`, `4aa002435`, and the full train/tunnel changes found conflicting
source context or materially larger behavioral surface; they were left out.

Upstream's later 45-file draw-plan rewrite (`31d16a980`) was reviewed but not
carried into this milestone. It removes per-object piece vectors and changes
renderer submission/order across many stages. A direct patch applicability
check fails in the current fork's renderer and dependent tests, so it needs a
manual port plus render-order regression coverage. The Z-shape shader/table
rewrite was deferred because it changes GPU visibility behavior and needs
Android image regression coverage.

The newer custom in-match music behavior is not the missing menu BGM fix reported
for Android and requires its own settings/resource integration. The Ares SHP vehicle
and alpha-light rendering work (`3b14fe75f`) is a concrete unported mod-rendering
gap: the upstream 47-file patch does not apply to the fork's SHP renderer, atlas,
and animation lifecycle layout. The shell geometry/Ares glow refactor
(`1c35033e4`) also has extensive context conflicts with the fork's UI tree. The
original-terrain networking change (`988a127a9`) spans asset selection and
multiplayer agreement; it remains unported pending a manual adaptation with
vanilla/mod asset-resolution and determinism tests. These features are not
claimed as implemented.

## Source changes in this pass

- `src/sim/scenario_post_map.rs`, `src/sim/economy.rs`,
  `src/sim/house_state.rs`: Ares and stock `FillSilos` behavior with regression tests.
- `src/rules/ruleset.rs`, `src/sim/trigger_runtime_ares.rs`: indexed Ares techno
  lookup and borrowed trigger house iteration.
- `src/sim/movement/`, `src/sim/pathfinding/zone_build.rs`,
  `src/sim/world/shroud_refresh.rs`: movement/pathfinding allocation and repeated
  scan reductions while preserving traversal and ordering semantics.
- `src/sim/gate_runtime.rs`, `src/sim/combat/line_of_fire.rs`,
  `src/sim/world/projectile_collision.rs`: avoid unnecessary set creation and
  `CellRef` cloning in the checked hot paths.
- `src/app/diagnostics/performance_overlay.rs`, `src/app/loading/transitions.rs`,
  `src/app/in_game.rs`, `src/app/shell_skirmish.rs`, `src/render/gpu.rs`: trim
  unnecessary GPU/UI resource lifetime and make optional GPU timer setup tolerant.
- `src/sim/ai_base_building.rs`, `src/sim/combat/`, `src/sim/economy.rs`,
  `src/sim/radar_jammer.rs`, `src/sim/superweapon/`, `src/sim/world/` and
  `src/map/resolved_terrain/`: remove verified temporary clones and preserve
  stable iteration/order behavior.
- `src/rules/ares_techno_type.rs`, `src/rules/superweapon_type.rs`: bound Ares
  prerequisite/paradrop vectors to current upstream's defensive limits; tests
  cover oversized and negative counts.

No simulation tick rate, gameplay rule, visual effect, or rendering feature was
removed for performance. No gameplay FPS improvement is claimed: no equivalent
Android gameplay capture with CPU/GPU/frame-time/memory measurements was available.
The existing RA2YRPORT performance captures use a different C++ engine and are not
an equivalent VERA20K+wGPU benchmark. The project's `sim-bench` requires a game
installation and map; no legally obtained playable installation is available in
the E: workspace, so a deterministic gameplay A/B timing run could not be made.

## Verification status

Passed focused checks on the Windows host:

- movement suite: 1,288 passed, 0 failed, 66 ignored;
- pathfinding zone tests: 38 passed;
- scenario post-map tests: 13 passed, including new `FillSilos` cases;
- Ares techno lookup regression: 1 passed;
- gate footprint regression: 1 passed;
- line-of-fire signed XYZ/obstruction regression: 1 passed;
- expiry-listener tests: 5 passed;
- Ares/Phobos oversized-count tests: 3 passed;
- forced Android JVM tests: 55 tests, 0 failures/errors/skipped;
- isolated `rustfmt --check` on all 44 changed Rust files and `git diff --check`;
- fresh host `cargo check -p vera20k --lib` after the latest source changes
  (warm recheck: 11.50s; full check immediately before it: 4m13s).

The expanded core test selection ran 5,932 tests: 5,787 passed, 12 failed and
133 were ignored. All 12 failures were reproduced on clean B49 (`aa77b7151`) with
the matching feature configuration. They include Phobos sleep-assertion
expectations, plus existing map-generator, Ares prerequisite, factory-AI and
replay-hash failures. They are not port regressions, but the broad suite is not
green and remains a known limitation.

An earlier optimized Android ARM64 native build measured 19m 56s. The current
source passed in a 19m 16s retry after an initial partial compile was interrupted
for process inspection; those durations are not comparable, so no build-speed
improvement is claimed. The current build used NDK 28.2.13676358, API 26,
`shipping`, and `phobos`. It produced a 76,500,808-byte ELF64/AArch64 library at
`E:\Temp\ih-upstream-arm64-final\arm64-v8a\libvera20k_android.so`, SHA-256
`F1582F9B01C2DE8BEB7B5C2BD07E908080FA010F1DC7CD10E26C5FE0B5FE1ADE`. All four
`PT_LOAD` segments use 0x4000 alignment. The API 26 audit scanned 21 NDK stub
libraries and found no unmatched strong imports; the only unmatched weak imports
are `copy_file_range` and `getrandom`. `AMotionEvent_getActionButton` is absent.
Details are in `out/logs/android-upstream-port-arm64-api26-symbol-audit.txt`; the
current build log is `out/logs/android-upstream-port-arm64-shipping-retry.log`.

The Android JVM suite was forced to execute with `:app:testDebugUnitTest
--no-daemon --rerun-tasks`: 55 tests across 14 XML reports, 0 failures, 0 errors,
0 skipped. The log is `out/logs/android-upstream-port-jvm-tests-rerun.log`.

No APK has been created. The existing release keystore file is present under the
Android module on E:, but `ANDROID_RELEASE_STORE_FILE`, `ANDROID_RELEASE_STORE_PASSWORD`,
`ANDROID_RELEASE_KEY_ALIAS`, and `ANDROID_RELEASE_KEY_PASSWORD` are not configured
in the build environment. I did not replace the signing identity. Consequently
Gradle release packaging/signature checks and APK-level 16 KB alignment checks
remain pending. `:app:assembleRelease --no-daemon` was attempted as a preflight and
stopped at the configured signing check before packaging; its log is
`out/logs/android-upstream-port-release-signing-preflight.log`. The Android 16
device is connected, but no APK was installed and no gameplay/thermal test is
claimed.

The GLES follow-up passed two focused configuration tests, ARM64 optimized
`cargo check` for `vera20k-android` with Phobos, Kotlin compilation, and the Android
JVM suite (55 tests, 0 failures/errors/skipped). A correctly targeted optimized
ARM64 native-library build did not complete: the first direct-Cargo attempt used
the host MinGW linker and failed at the Android link step; the cargo-ndk retry
entered prolonged engine code generation and was stopped without an error result.
Logs are `out/logs/gles-fallback-android-package-build.log` and
`out/logs/gles-fallback-cargo-ndk-build.log`. An earlier unqualified workspace
build selected default desktop members and was stopped; it produced no APK.
The ARM64 `cargo check` passed, but a linked `.so`, APK packaging, signatures,
16 KB APK alignment, and Android GLES device validation remain outstanding.

## Upstream release notes

- [v0.3.7](https://github.com/YuriPlanet/vera20k/releases/tag/v0.3.7)
- [v0.3.8](https://github.com/YuriPlanet/vera20k/releases/tag/v0.3.8)
- [v0.3.9](https://github.com/YuriPlanet/vera20k/releases/tag/v0.3.9)
- [v0.4.0](https://github.com/YuriPlanet/vera20k/releases/tag/v0.4.0)

## Current-main follow-up (2026-11-23)

The upstream audit was refreshed through `origin/main` at
`41f140f93db4d28dcc5b8f4f4a839593bf8de787`, after the previously reviewed head
`eb05f451e965337534a68d631a2217a242271b36`.

Selective integrations and focused host tests in this follow-up:

- `f39764cd1`: Ares `Pip`/`OccupyPip` accepts named values and nonnegative
  integers while retaining the existing frame for absent or invalid values.
- `d00f8183c`: Ares `AlternateTheaterArt` selects a theater-specific image only
  when that art section exists; stock alternate-arctic behavior is unchanged.
- `da9fe8cdf`: Ares EMP can interrupt a Tunnel mover's dig-in; the regression
  test checks that it returns to surface behavior.
- `3ded72cf2`: the NPExt `DisableAIParallelProduction` name is accepted as the
  fallback for the existing parallel-AI setting, with existing-name precedence.
- `856f43752`: stock-YR classification now checks all eleven configured INI
  roots and the theater suffix. A custom EVA root alone is covered by an app
  regression test.

The three Ares regressions and one AI setting regression, both NPatch
alias/precedence tests, the EVA root identity test, and the regular plus
forced-GLES terrain fallback tests passed. Logs are in
`out/logs/upstream-main-mod-compat-regressions.log`,
`out/logs/upstream-main-npatch-parallel-ai-tests.log`,
`out/logs/upstream-main-eva-resource-identity-test.log`,
`out/logs/upstream-gles-terrain-fallback-test.log`, and
`out/logs/upstream-gles-terrain-native-copy-test.log`.

The selected mobile-Techno Ares radial indicator from `c3b4ec836` has now been
adapted through the existing HUD capture and renderer, with a regression test
for Ares/stock behavior and house ownership. Its test did not execute: rustc
could not write incremental metadata because E: had only 4 KB free. The same
space error stopped an attempted stale-cache cleanup at the command safety
policy; the cache was left untouched, and this port is not yet verified.

The wide-map distance correction is now wired through FireAt launch distance,
rocket pitch, Sonic-wave wobble, and wave-constructor distance. Static review
found that this fork stores `map_width` as the canonical cell-array extent
(`SizeW + SizeH`), so the VERA-only gate must be `map_width > 512`; adding
`map_width + map_height` would widen arithmetic too early. The source and
512/513 boundary regression were corrected, but the focused test could not run:
Cargo failed to create an output directory with `os error 112` because E: has
zero free bytes. Original-terrain behavior for vanilla network matches still
needs a separate compatibility/regression pass. Relay-service protocol and
web/lobby changes do not map directly to the Android engine work. This
follow-up has not produced a new native library or APK; Android/device behavior
for these source changes remains unverified.
