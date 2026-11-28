# Iron Harbor Mobile handoff

## Current CI checkpoint — parallel Android workflow (2026-11-28)

- Branch `android-new` is pushed at `f58d1b5dcd6859b168ff4cd9f839251b7fb3045c`. This commit replaces the serialized Android build path with a source-freeze job, then parallel Rust tests, launcher JVM tests, and one optimized ARM64 native build. Packaging, signing, and APK verification wait for all three jobs and consume the native library artifact; they do not compile Rust again. Job checkouts assert the same SHA. Cargo caches are separated by host-test vs ARM64 profile; Gradle cache is populated by launcher tests and read-only in package.
- Workflow validation run [46129445142](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46129445142) is still running on exact source SHA `f58d1b5d...`: prepare passed; launcher JVM tests passed (61 s); Rust tests and ARM64 compilation are concurrent. Await final native artifact handoff, package/signature checks, and run conclusion before calling the refactor validated. No local compilation ran.
- Baseline run [46121114314](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46121114314) took 392 s for engine tests plus 260 s for ARM64 compilation sequentially (652 s combined). Expected saving is about 260 s from overlapping these two stages; report actual new duration only after run completion.
- B61 run [46127483526](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46127483526) built and verified a signed APK and uploaded artifact [15256828989](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46127483526/artifacts/15256828989), but release publication failed HTTP 403. The target commit changes `.github/workflows` relative to the fork default branch `main`; GitHub's Create Release endpoint requires `Workflows: write` in addition to contents write, a permission unavailable to `GITHUB_TOKEN`. There is no public B61 release/tag. A fail-fast guard and optional `ANDROID_RELEASE_GITHUB_TOKEN` support are staged locally after `f58d1b5d`; commit these after checking the current workflow run. The fine-grained token needs this repository's Contents: write and Workflows: write.
- Preserve untracked `out/` and do not compile locally. The current parallel workflow modifies only CI orchestration; no device/gameplay claims follow from it.

## Current checkpoint — touch box selection and diagnostic toggle (2026-11-28; CI pending)

- Repository `shietgrupe-cpu13/IronHarborMobile`, branch `android-new`, current remote base `b1135666a` (fast-forwarded from `9ed5f2b3` after the upstream sync workflow advanced). Actions [46117788398](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46117788398) succeeded and uploaded artifact [15252099855](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46117788398/artifacts/15252099855). That artifact predates both the upstream fast-forward and the current uncommitted changes.
- Current tracked edits are limited to `src/app/input/touch.rs`, `StartupDiagnostics.java`, `LauncherActivity.kt`, this handoff and `PROGRESS.md`. Untracked `out/` is user data: leave it untouched and exclude it from commits.
- Latest IHLOGS: Xiaomi 2412DPC0AG, Android 16 / ARM64, app 0.4.3 code 208; last preflight 25 ms; latest engine startup selected Mali-G720 Vulkan, while earlier sessions selected OpenGL ES. Input log shows touch motion counts but no engine gesture classification. Source review identified long-hold jitter firing right-click before selection and the delayed-drag origin reset; both are corrected with focused touch regression tests.
- Diagnostics tab now has a persistent toggle for automatic startup snapshots. Disabling it leaves engine logs, manual ZIP export and crash reports available. Automatic preference reads occur in the background capture worker to avoid blocking Application startup.
- Performance evidence is limited to atlas-growth timings (roughly 13–138 ms chunks; 5–13 ms main-thread preparation); no equivalent gameplay FPS samples are in IHLOGS. Engine inspection confirms Rayon workers for CPU-side work and a dedicated simulation thread, while wgpu handles GPU rendering. Do not claim an FPS gain; profile identical gameplay workloads before optimizing.
- No local Rust/Cargo/Gradle compilation or tests. Next: review diff, commit only tracked source/docs, push to `android-new`, monitor Actions (which already filters `app::input::touch::`), fix actual CI failures, then ask the user to verify held-drag selection and the diagnostics toggle on-device. Preserve C1..C10, swipe pan, mod maps and faction flags. Cloak/submersible appearance still needs a device screenshot/capture with a named unit and mod.

## Current checkpoint — skirmish mode/map regression (2026-11-28)

- Branch `android-new`, base `dbb8c7526db50f116469febb183194ff6236f373`, remote `vera20k-fork/android-new` matched at last fetch. Worktree contains a focused uncommitted source/test/workflow patch plus documentation; untracked `out/` is user data and must remain untouched.
- Latest IHLOGS A/B: the same 964-byte `MPModesMD.ini` from `ra2md.mix -> localmd.mix` succeeds with another mod; Red Resurrection has zero usable rows after client map-mode filtering. Root cause in code: strict client map mode intersection removes all native roster entries when no client mode maps to native `map_filter` values. Shared mod maps are staged at `Documents/RA2Harbor/Maps`, but the client-catalog branch previously returned before scanning that directory.
- Patch: preserve native modes if a client catalog has no native-mode intersection; keep strict filtering when there is an intersection. Add shared Maps files to the client catalog path. Focused regression covers a legacy catalog (`[MultiMaps]` memberships without `[GameModes]`) and staged `standard` map.
- Runs [46105658592](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46105658592) and [46106412415](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46106412415) both passed JVM and earlier Rust filters but failed on test-fixture setup, not production source. First failure: `with_mod` alone did not attach `ClientInstall`; second: `audio_test_install` lacks `MPModesMD.ini`. Fixture now uses production `mod_resources`/`with_resource_config` and writes one synthetic native `standard` roster row. No ARM64 build/APK was produced. `git diff --check` passed; no local compilation/tests. Next: commit fixture correction, push, monitor Actions log/artifact, fix deterministic failures, then ask user to retest Choose Map and staged-map launch. CI/device result remains unverified.
- Residual: device logs do not expose the launcher staging manifest or prove staged maps were actually loaded. Device test must confirm Red Resurrection mode rows and a staged map appear, map selection loads that map, mod switch/unselect removes only unchanged managed copies, and personal maps remain. Do not claim device verification or a performance gain.

Updated 2026-11-28. The authoritative specification is [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## Current unverified change — Maps migration stall

- Worktree branch is `android-new` at pushed commit `cebb86b8e4ea367e9cbb7b6c532d6d436c135c6c`; last synced base was `64d374eac`. Untracked `out/` must remain untouched.
- Current source diff optimizes `SharedDataStorage.mergeLegacyDirectory`: target names are indexed once per directory rather than rescanned for each legacy entry. A focused JVM regression checks case-insensitive collision handling and preservation of an occupied legacy suffix.
- Device evidence: latest startup diagnostic reports 7,711 ms in `documents_layout_check` of 7,747 ms preflight. `ensureLayout()` calls the old `Configs/Maps` migration; its previous nested directory rescans are the identified unbounded work. This is a source-based root-cause attribution; there is no equivalent before/after device timing yet.
- GitHub Actions [46095669654](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46095669654) passed Kotlin/JVM, focused Rust, ARM64 native build, APK assembly, signing, package/alignment checks and artifact upload. Artifact [15243652837](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46095669654/artifacts/15243652837) is 54,431,654 bytes. This is CI verification only; there was no local build or device timing retest.
- Separate remaining gameplay issue: latest logs report zero usable mode rows in `MPModesMD.ini`. The log also reports 497 mod maps staged in canonical `Documents/RA2Harbor/Maps` and no `Configs/Maps`. Mode roster/resource resolution still needs root-cause work; do not fabricate fallback modes or attribute it to this migration fix.
- Next: ask the user to install/test the artifact against an equivalent selected mod/map workload and compare `documents_layout_check`; separately investigate why the selected mod's effective mode roster is empty before claiming Choose Map works.

## Repository and verified build

- Repository `shietgrupe-cpu13/IronHarborMobile`, branch `android-new`; last synced local and remote base `64d374eac`. Current worktree includes the uncommitted Maps migration fix and its JVM regression; CI has not run for this diff.
- The last source commits before CI's upstream sync were `96b0c6b87` (temporary selected-mod map staging), `e2b8227ba` (canonical Maps path correction), `99211b28d` (resolve `VeraActivity.kt` upstream conflict), and `f4ef8bbf1` (fix renamed map-root test filter).
- Actions [46090355627](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627) passed Kotlin/JVM tests, focused Rust regressions, ARM64 native compilation, APK assembly, persistent signing, package/signature/alignment checks, artifact upload and upstream merge push.
- Debug APK artifact [15241902961](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46090355627/artifacts/15241902961), 54,431,155 bytes, SHA-256 digest `e9e96beb5bc217792df712842c8fef3e197b89296f6c754263bbc1a1e40b0b15d`; expires 2026-12-12. This is an Actions artifact, not a public release. No device test occurred.
- Preserve backup and `android-development` refs. The only untracked path is `out/`; do not inspect, stage, delete or alter it.

## Current map behavior and limits

- Intended behavior: when a mod is selected, stage its supported loose maps under `Documents/RA2Harbor/Maps`; when switching/unselecting, remove only unchanged managed copies. Keep source mod maps and user maps intact.
- `LauncherModMapStaging` recursively stages `.map`, `.yrm`, and `.mpr`, tracks ownership and hashes in a manifest, and is invoked on the launcher I/O executor. Startup avoids repeating a scan when the saved selection is already synchronized. Profile-only changes do not rescan.
- The user clarified that `Documents/RA2Harbor/Configs/Maps` was wrong. The engine resolver now returns `Documents/RA2Harbor/Maps`; launcher imports and folder creation use it too. Prior `Configs/Maps` files are migrated non-destructively into `Maps`, preserving collisions with a unique legacy filename.
- CI tests cover staging, source preservation, mod switch/unselect cleanup, edited/user map retention, old-path migration and the engine path resolver. Physical-device verification is still required to confirm rows appear in Choose Map and cleanup works with the user's real mod.
- The latest IHLOGS had no loose map rows and reported unusable `MPModesMD.ini` rows. Staging is intended to address visibility of loose nested maps; it does not fix the independent mode-catalog warning or prove actual map launch behavior.

## Preserve these existing decisions

- VERA20K upstream owns engine/game behavior; Iron Harbor is the mobile frontend. Preserve the original launcher, direct read-in-place game access, `Documents/RA2Harbor`, mod and map handling, Vulkan/OpenGL ES choices, and the dynamic in-game HUD.
- Do not alter `android-development`, user data, or untracked `out/`. Do not force-push. Do not compile Rust, Cargo, Gradle, tests or APKs locally; use GitHub Actions.
- Avoid performance claims without equivalent before/after measurements. Do not call CI success physical-device validation. Do not publish a release or submit an upstream PR without approval.

## Remaining work

1. Ask the user to install the debug artifact and test a mod with nested maps: confirm rows appear in Choose Map, switch to another mod and vanilla, verify only generated copies are removed, and confirm personal maps remain under `Documents/RA2Harbor/Maps`.
2. If the map list remains empty, collect the new `IHLOGS` report and trace the actual chosen catalog/profile; distinguish map-file discovery from the `MPModesMD.ini` warning.
3. Continue device validation of MO/RR resources, renderer selection, launcher latency, HUD persistence/touch behavior and clean shutdown. `DeleteGlobalRef` ownership remains unproven; the earlier HWUI tombstone is not its cause evidence.
4. Keep the SAF-copy fallback, config migration edge cases and other unfinished Phase 2–5 items on the master plan. Maintain progress/roadmap/upstream/device documents after verified milestones.

## Upstream synchronization note

The failed run [46089750189](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46089750189) stopped at an upstream conflict in `VeraActivity.kt`. It was resolved by retaining both the Iron Harbor held-input release and delayed shutdown handling and upstream's mod ZIP progress-dialog cleanup. A subsequent run initially exposed an obsolete test filter; `f4ef8bbf1` changed it to the current shared Maps path test name. Run 46090355627 then passed, and CI pushed the verified upstream merge at `b94e433a9`.
