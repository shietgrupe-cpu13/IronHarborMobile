# Proposal: reusable mobile controls and Android usability for VERA20K

**For:** hrusten and VERA20K maintainers  
**From:** Iron Harbor Mobile contributors  
**Status:** discussion draft; no upstream contact or PR has been made  
**Compared revisions:** Iron Harbor B60 `1293fb441727e2522f6a284d88faf4f108a1ee3e`; upstream `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`.

## Summary

Upstream has established an Android native build, SAF-based game installation
import, and Android soft-keyboard support through GameActivity. Iron Harbor has
experimented with a separate touch-first launcher, direct local-folder
selection, profile and map management, diagnostics, and a customizable floating
touch HUD. We would like to contribute the pieces that are broadly useful to
mobile players, without bringing Iron Harbor branding, a second engine input
owner, broad storage permissions, or unverified compatibility claims into
VERA20K.

Our strongest new contribution candidate is a small, optional, upstream-styled
touch HUD with a user-adjustable layout, built on the existing engine input
actions. Touch-only users could move and resize controls for their device while
keeping the original game command behavior. The implementation should be
adapted to the current GameActivity and lifecycle rather than copying Iron
Harbor's NativeActivity wholesale.

## What we found

- Upstream commit `21fa6d2f305c79fa210992885ea0845fd2c58ba7` (#4350) provides
  Android GameActivity and IME composition for game text fields. We consider
  that upstream feature already implemented; it should not be duplicated.
- Upstream commit `84ff1d6c146fb602585131b7b49d44c81b2a7cb1` (#5220) adds folder
  actions and mod shortcuts to the in-game Mods page. Iron Harbor's launcher
  catalog and user flow are complementary, but should call the engine's single
  mod/resource resolver rather than introduce another resolver.
- Iron Harbor B60's `TouchHudSettings`, `TouchHudLayoutEditor`, and
  `TouchHudOverlay` contain a working design direction for normalized layout
  preferences, visible controls, sizing, control groups and key dispatch.
  Their behavior is not being represented as upstream-tested; they need
  cleanup and device validation against GameActivity.
- Iron Harbor's direct filesystem access uses a SAF picker to choose local
  folders, then requires `MANAGE_EXTERNAL_STORAGE` to give Rust ordinary paths.
  We do not recommend copying that permission model into upstream. Upstream's
  current staged SAF import is more provider-compatible but duplicates data.
  True provider-backed reads would require an engine-level asset source
  abstraction, not a manifest change alone.
- The Iron Harbor build workflow has more cache and APK validation steps than
  the workflow currently proposed in open PR #5256. We do not suggest a
  competing workflow; a small follow-up can be considered after that PR is
  resolved.

## Suggested first implementation

Start with a reviewable Android-only touch layout feature:

1. Define a small set of actions that already map to upstream engine input.
2. Persist only layout and accessibility preferences (visibility, normalized
   placement and size); do not persist simulation state or duplicate control
   group membership.
3. Provide one simple move/resize/reset workflow using upstream's visual style.
4. Route button actions through the same game input owner as physical keys.
5. Keep the overlay disableable and separate from the Android IME.

Tests should cover layout parsing/clamping and key release in JVM tests. Add Rust
input tests only if the engine input owner changes. Physical checks should cover
touch-only and keyboard/mouse use, gesture navigation, portrait/landscape,
display cutouts, pause/resume, focus loss and Android 12–16. No frame-rate
improvement is part of the proposal; performance must be measured separately if
the overlay is suspected of adding cost.

## Boundaries and safety

- Vanilla Yuri's Revenge behavior remains the default.
- The HUD is presentation/input convenience only; simulation rules, update order,
  multiplayer protocol and deterministic state do not change.
- Resource selection and mod compatibility remain engine-owned and data-driven.
- Do not copy Iron Harbor branding, logo, promotional dialogs, app-specific
  paths, signing configuration, EA artwork, or user game files.
- Preserve GPLv3 notices and dependencies' required attribution. Any image or
  icon must have clear redistribution permission and attribution.
- Contributions should follow the upstream README's contributor guidance and
  remain small. We can separate work into a neutral layout model and a basic
  Android view/overlay only if maintainers prefer that split.

## Other possible follow-ups

After maintainers review the HUD direction, we can discuss: a more explicit
engine-resolved mod summary for launcher selection, bounded diagnostic bundle
export, and Android CI metadata/alignment checks after PR #5256 lands. We would
first verify whether upstream now exposes the required API or has already
implemented the same behavior.

## Open questions for maintainers

1. Is a touch HUD within upstream's desired Android UX scope, or should Android
   remain limited to generic keyboard/touch input?
2. Which upstream action identifiers and event injection path should a HUD use
   so touch controls remain identical to keyboard shortcuts?
3. Would the maintainers prefer an initial default layout only, with editing in
   a later contribution?
4. What Android API/device matrix is expected for an Android-specific feature
   PR?
5. For storage, is app-private staged import the intended supported model, or is
   an engine asset-provider abstraction worth designing separately?

## References

- [Upstream Android guide at `d1e28004aa17dc445cbfee450cfe6fa55c5fe949`](https://github.com/YuriPlanet/vera20k/blob/d1e28004aa17dc445cbfee450cfe6fa55c5fe949/docs/android.md)
- [Upstream contribution guidance](https://github.com/YuriPlanet/vera20k#contributing)
- [Upstream GameActivity/IME change #4350](https://github.com/YuriPlanet/vera20k/commit/21fa6d2f305c79fa210992885ea0845fd2c58ba7)
- [Upstream Mods page actions #5220](https://github.com/YuriPlanet/vera20k/commit/84ff1d6c146fb602585131b7b49d44c81b2a7cb1)
- [Android workflow PR #5256](https://github.com/YuriPlanet/vera20k/pull/5256)
- [Iron Harbor B60 Android source](https://github.com/shietgrupe-cpu13/IronHarborMobile/tree/1293fb441727e2522f6a284d88faf4f108a1ee3e/android)
- [Upstream GPLv3 text](https://github.com/YuriPlanet/vera20k/blob/d1e28004aa17dc445cbfee450cfe6fa55c5fe949/LICENSE-GPL)
