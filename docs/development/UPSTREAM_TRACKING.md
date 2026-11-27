# Upstream tracking

Updated 2026-11-27.

## Current Android regression work (unverified)

The current working tree adds Android launcher renderer persistence on top of
upstream's `GraphicsApi`/wgpu initialization path, and explicit MO resource
selection through existing `IniRootConfig`, `ModDefinition` and
`AssetManager` owners. These are Android integration/compatibility changes;
they are not yet validated against an actual MO installation or device. The
new workflow runs focused tests before ARM64 APK assembly. No upstream PR is
prepared or submitted.

## Revision relationship

- Upstream `YuriPlanet/vera20k` `main` fetched at `814b6c3c043fc18c0952576e4f4bca9745981fe0` (`Add a driver policy for externally controlled computer houses`, PR #4576).
- Iron Harbor fork `android-new`: `6afcd67980f39497f9b6e99c6243148a7deecf08`; upstream `814b6c3c0` is integrated at merge `ac39fc2db`. Upstream sync/build validation [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660) passed and uploaded an ARM64 debug APK. Current mod diagnostics commit validation is run [45995480713](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45995480713).
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

## Current integration status

The Android map/campaign change at `ebc52b3fd` passed hosted validation in run [45992723660](https://github.com/shietgrupe-cpu13/IronHarborMobile/actions/runs/45992723660). It removes the launcher campaign page and uses `Configs/Maps` for Android while preserving non-Android `Maps`, with a non-destructive legacy map migration. It has not been device-tested.

The newly fetched upstream commit adds a selectable driver policy for offline computer houses, carries it into simulation/save state, and explicitly refuses it in network rooms. It is upstream gameplay/simulation work, not Android frontend code. Do not selectively port it; let the existing sync workflow validate and merge it as a complete upstream commit, then run its focused regressions as part of that workflow.

## Upstream compatibility notes

- Current upstream Android uses `GameActivity`, winit and path-based `AssetManager`; SAF copy is the existing no-broad-permission flow. Persisted SAF grants alone do not make native `Path` reads possible.
- Current Android documentation says Vulkan is required and GLES-only devices cannot install. Do not port the legacy GLES selector as a working feature without changing and validating the actual backend.
- The legacy branch includes launcher, storage, profile, maps, diagnostics and HUD features; each requires API/ownership adaptation. No engine or simulation rewrite is planned.
- No external upstream PR has been opened. Prepare neutral candidate scopes after feature validation; submission requires user approval.
