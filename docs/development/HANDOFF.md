# Iron Harbor Mobile handoff

## Current checkpoint — skirmish mode/map regression (2026-11-28)

- Branch `android-new`, base `dbb8c7526db50f116469febb183194ff6236f373`, remote `vera20k-fork/android-new` matched at last fetch. Worktree contains a focused uncommitted source/test/workflow patch plus documentation; untracked `out/` is user data and must remain untouched.
- Latest IHLOGS A/B: the same 964-byte `MPModesMD.ini` from `ra2md.mix -> localmd.mix` succeeds with another mod; Red Resurrection has zero usable rows after client map-mode filtering. Root cause in code: strict client map mode intersection removes all native roster entries when no client mode maps to native `map_filter` values. Shared mod maps are staged at `Documents/RA2Harbor/Maps`, but the client-catalog branch previously returned before scanning that directory.
- Patch: preserve native modes if a client catalog has no native-mode intersection; keep strict filtering when there is an intersection. Add shared Maps files to the client catalog path. Focused regression covers a legacy catalog (`[MultiMaps]` memberships without `[GameModes]`) and staged `standard` map.
- `git diff --check` passed. Do not run local Rust/Cargo/Gradle/APK compilation. The focused test is wired into Actions but has not run. Next: finish review, inspect diff, commit only intended files (never `out/`), push to `vera20k-fork/android-new`, monitor Actions logs and artifacts, fix deterministic failures, then ask user to retest Choose Map and launch a staged mod map. CI/device results are not yet verified.
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
