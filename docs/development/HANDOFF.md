# Iron Harbor Mobile handoff

Updated 2026-11-28. The authoritative specification is [IRON_HARBOR_MASTER_PLAN.md](IRON_HARBOR_MASTER_PLAN.md).

## Current unverified change — Maps migration stall

- Worktree branch is `android-new`; last synced base was local `64d374eac`. Untracked `out/` must remain untouched.
- Current source diff optimizes `SharedDataStorage.mergeLegacyDirectory`: target names are indexed once per directory rather than rescanned for each legacy entry. A focused JVM regression checks case-insensitive collision handling and preservation of an occupied legacy suffix.
- Device evidence: latest startup diagnostic reports 7,711 ms in `documents_layout_check` of 7,747 ms preflight. `ensureLayout()` calls the old `Configs/Maps` migration; its previous nested directory rescans are the identified unbounded work. This is a source-based root-cause attribution; there is no equivalent before/after device timing yet.
- CI has not run for this diff. Do not claim the fix verified until the Android JVM workflow passes. No local Kotlin/Rust/Gradle build was run.
- Separate remaining gameplay issue: latest logs report zero usable mode rows in `MPModesMD.ini`. The log also reports 497 mod maps staged in canonical `Documents/RA2Harbor/Maps` and no `Configs/Maps`. Mode roster/resource resolution still needs root-cause work; do not fabricate fallback modes or attribute it to this migration fix.
- Next: inspect the focused diff, push the owned commit to `android-new`, monitor the Actions run, fix any actual failure, and update this handoff with its result/artifact. Physical-device retest is needed to compare startup and verify the map chooser independently.

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
