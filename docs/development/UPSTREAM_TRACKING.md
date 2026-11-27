# Upstream tracking

Updated 2026-11-28.

## Latest source/CI checkpoint (2026-11-28)

- `origin/main` remains at `068e965efcf27bfccc59fab3663fe7bded3788be` (upstream `ec197f8a9`, PR #5323), a documentation/contract update; no later upstream change was incorporated in Actions run `46046233771`.
- Commit `8e02d79b80eedca143c1c900bd5a7eb45fb83675` passed run [46050368117](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117), including Kotlin/JVM, focused Rust, ARM64 and APK/package checks. Artifact [15225361525](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46050368117/artifacts/15225361525) is a build-only ARM64 debug artifact, not physical-device evidence.
- `1703630f7` integrates configured launcher HUD controls with the GameActivity overlay and connects game editing to the existing layout settings/editor. JVM regression tests passed. This remains an Android fork feature, not an upstream contribution proposal.
- The HUD is not feature-complete: dynamic add/duplicate/rename, named profiles, broader modifier chords and device validation remain. Per-button opacity is in the pending follow-up below. MO/RR gameplay, renderer runtime, startup/switch latency and JNI shutdown remain device/manual or source investigations.

Follow-up `8e02d79b8` adds per-button opacity to the existing editor and schema-v4 HUD backup, while preserving v1-v3 imports. It is fork-specific and no upstream-neutral proposal/PR is prepared.

Commit `57c066a9b` adds validated labels for existing HUD actions and schema-v5 export/import while keeping v1-v4 compatibility. Run [46053869832](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46053869832) passed; this remains a fork feature without an upstream-neutral proposal/PR.

## Prior source/CI checkpoint (2026-11-27; superseded)

- `origin/main` fetched at `068e965efcf27bfccc59fab3663fe7bded3788be`; the new upstream history is `ec197f8a9` and merge PR #5323, a documentation/contract change relaxing a performance-hash requirement. No engine or Android implementation change was present in this update.
- Fork `android-new` is at `e1af36488dc270b73578fdc9166e63f97a52dc43`, with the upstream merge pushed by workflow [46042175052](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052). The run passed focused tests and ARM64 APK validation; artifact [15222025462](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/46042175052/artifacts/15222025462) is build-only evidence.
- Latest fork-specific commits: `9556681c0` adds the guarded legacy client-map mode inference; `f4d94a34c` unifies in-game overlay positions with portable launcher HUD settings; `aa6b0949d` and `44f5ce7a1` correct JVM test imports/legacy fixtures. No external upstream PR is prepared or submitted.
- These changes pass source tests only. Android-specific behavior stays fork-only pending POCO test. The full HUD editor, true mod resource selection on device, AAudio/JNI shutdown ownership, SAF fallback and renderer runtime remain open. Do not claim startup or FPS gains from these tests.

## Historical Android regression note (superseded by the latest checkpoint above)

The current working tree adds Android launcher renderer persistence on top of
upstream's `GraphicsApi`/wgpu initialization path, and explicit MO resource
selection through existing `IniRootConfig`, `ModDefinition` and
`AssetManager` owners. These are Android integration/compatibility changes;
they are not yet validated against an actual MO installation or device. The
new workflow runs focused tests before ARM64 APK assembly. No upstream PR is
prepared or submitted.

## Revision relationship

- Upstream `YuriPlanet/vera20k` `main` fetched at `814b6c3c043fc18c0952576e4f4bca9745981fe0` (`Add a driver policy for externally controlled computer houses`, PR #4576).
- At this historical checkpoint Iron Harbor fork `android-new` was `6afcd67980f39497f9b6e99c6243148a7deecf08`; upstream `814b6c3c0` is integrated at merge `ac39fc2db`. Upstream sync/build validation [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed and uploaded an ARM64 debug APK. The mod-diagnostics validation run `45995480713` was later superseded; see the latest checkpoint above.
- Legacy mobile reference: `android-development` at `1293fb441727e2522f6a284d88faf4f108a1ee3e`; backup branches retained. Do not merge it wholesale.
- Internal sync PR #1 remains to be reviewed/merged into the default branch before scheduled synchronization can be relied upon.

## Existing Android contributions

- `302be2b45` movable Android HUD (included with upstream HUD work / PR #5295).
- `a725dc756` launcher/JVM validation workflow.
- `412226bcc` validated Android branch synchronization workflow.
- `3eb6f6b6c` bounded retry for transient Gradle dependency/network failures.
- `2e19b4453` public Android API-compatible HUD key forwarding and CI ordering.
- `d3f9e61be` Android-compatible JVM test file operations.
- `529cbe103` uploaded ARM64 debug APK artifact from Actions.
- `c533ca3eb`, `d94bfba63`, `ea2bc9995` development plan/progress/handoff and device report/master plan documentation.

## Historical integration status (2026-11-27)

The Android map/campaign change at `ebc52b3fd` passed hosted validation in run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660). It removes the launcher campaign page and uses `Configs/Maps` for Android while preserving non-Android `Maps`, with a non-destructive legacy map migration. It has not been device-tested.

At this checkpoint the newly fetched upstream commit added a selectable driver policy for offline computer houses, carried it into simulation/save state, and explicitly refused it in network rooms. It was upstream gameplay/simulation work, not Android frontend code. The later synchronization merge is recorded above; use the current upstream tracking checkpoint for present status.

## Upstream compatibility notes

- Current upstream Android uses `GameActivity`, winit and path-based `AssetManager`; SAF copy is the existing no-broad-permission flow. Persisted SAF grants alone do not make native `Path` reads possible.
- Current Android documentation says Vulkan is required and GLES-only devices cannot install. Do not port the legacy GLES selector as a working feature without changing and validating the actual backend.
- The legacy branch includes launcher, storage, profile, maps, diagnostics and HUD features; each requires API/ownership adaptation. No engine or simulation rewrite is planned.
- No external upstream PR has been opened. Prepare neutral candidate scopes after feature validation; submission requires user approval.
