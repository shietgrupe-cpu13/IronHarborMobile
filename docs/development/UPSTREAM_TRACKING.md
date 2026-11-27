# Upstream tracking

Updated 2026-11-27.

## Revision relationship

- Upstream `YuriPlanet/vera20k` `main`: `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`.
- Iron Harbor fork branch `android-new`: `ea2bc99959c47eda1bbc297851e8d1c2b2a33c9e` at last fetch; 11 fork commits ahead, 0 upstream commits missing.
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

## Current uncommitted integration

A selective storage/config slice is under review in the worktree: maps local primary-volume SAF selections to real filesystem paths only after explicit broad-access consent; preserves SAF-copy fallback; validates the game root through the engine; reuses existing data-root spellings; writes Android config under `Config/`; and carries a selected data-root pointer into native startup. It adds focused JVM and Rust config-path regression tests. Do not describe it as integrated, green or device-verified until committed, pushed and validated.

## Upstream compatibility notes

- Current upstream Android uses `GameActivity`, winit and path-based `AssetManager`; SAF copy is the existing no-broad-permission flow. Persisted SAF grants alone do not make native `Path` reads possible.
- Current Android documentation says Vulkan is required and GLES-only devices cannot install. Do not port the legacy GLES selector as a working feature without changing and validating the actual backend.
- The legacy branch includes launcher, storage, profile, maps, diagnostics and HUD features; each requires API/ownership adaptation. No engine or simulation rewrite is planned.
- No external upstream PR has been opened. Prepare neutral candidate scopes after feature validation; submission requires user approval.
