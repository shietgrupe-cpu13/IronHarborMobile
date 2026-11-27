# Iron Harbor → VERA20K contribution roadmap

This roadmap is based on the source audit in [UPSTREAM_GAP_ANALYSIS.md](UPSTREAM_GAP_ANALYSIS.md).
It is a proposal only: no source changes, issues, pull requests, or maintainer
messages have been made.

## Recommended sequence

### 0. Establish a clean contribution base (required before code PRs)

Iron Harbor B60 and upstream `main` diverge substantially. For each PR, start
from a fresh upstream `main` checkout and port one self-contained behavior; do
not merge the fork wholesale. Keep Iron Harbor-only package ID, branding,
storage paths, release keys, launcher styling, promotional content, and
distribution policy out. Preserve GPL attribution and third-party notices for
anything carried over. The project contract asks outside contributors to follow
upstream README contribution rules and keep PRs small.

### 1. Neutral customizable Android touch controls (strongest net-new mobile UX)

**Why first:** upstream has native Android and text input but no equivalent
customizable touch HUD/layout editor. This gives touch-only players adaptable
controls without changing simulation or game rules.

**Scope:** propose a minimal touch overlay with a small set of engine-supported
actions and user-adjustable position/visibility/scale. Keep layout coordinates
normalized to the activity's content/safe-area rectangle; persist only
presentation preferences. Use the existing engine input APIs for actions rather
than making Android own gameplay state. Start with one layout workflow; defer
profile syncing, import/export, decorative skins and large shortcut catalogs.

**Iron Harbor source to study:** `TouchHudSettings`, `TouchHudOverlay`,
`TouchHudLayoutEditor`, `TouchHudKeyLatch`, `VeraActivity.sendHudKey`,
`VeraActivity.sendHudControlGroup`, and Android JNI `nativeHudKey` /
`nativeHudControlGroup` in B60. Adapt these against upstream's `GameActivity`,
IME, input event flow, insets, and lifecycle. Do not port the Android Activity
class wholesale.

**Tests / acceptance:** focused JVM tests for normalized coordinate persistence,
layout bounds and default action mapping; Rust input regression tests only where
the upstream production input owner changes. Android-device review must confirm
portrait/landscape, gesture navigation, two-finger selection/camera gestures,
keyboard/mouse path unaffected, no stuck keys on pause/destroy, and sensible
safe areas. No FPS claim unless profiled.

**Risk / complexity:** medium-high; overlay event interception and GameActivity
surface/lifecycle interactions are device-sensitive. No new dependencies should
be needed if platform views and current engine input hooks suffice. Keep it
brand-free and opt-in/disableable.

### 2. Build workflow hardening, only after PR #5256 resolves

PR #5256 already supplies an ARM64 release workflow. Do not open a second
workflow PR while it is unsettled. If maintainers want a follow-up, offer
isolated improvements from Iron Harbor's workflow: pinned actions, Cargo cache,
focused Android JVM tests, APK identity/ABI/signature/16-KiB checks, artifact
retention and build logs. Keep publishing permission limited; fork signing
credentials must not travel upstream. Avoid duplicate workflow triggers and
release-tag semantics.

**Acceptance:** a pull request/manual standard-runner build creates an artifact;
the workflow checks its package ID, ABI, signature, and native alignment;
failure logs are retained; no signing secret is required for PR builds; caches
are keyed safely. Compare action versions to the upstream repository's accepted
policy at implementation time.

**Risk / complexity:** low-medium, but action pin/version maintenance and tag
release permissions need maintainer agreement. No compilation was run for this
audit.

### 3. Mobile-friendly asset and installation selection

Upstream already has SAF folder selection and app-private import. Iron Harbor
adds direct local-folder selection and shared data. Before proposing anything,
agree on the storage contract. The existing Iron Harbor route requires
`MANAGE_EXTERNAL_STORAGE`; replacing that with SAF alone is not a Kotlin-only
change because Rust's normal asset readers expect filesystem paths. A genuinely
broader provider implementation likely needs an engine-owned asset-source/VFS
interface or a narrowly scoped staging/cache design with explicit space and
immutability semantics.

**Propose only a bounded upstream improvement:** clearer validation/progress and
atomic/cancellable import behavior around upstream's current SAF copy, or an
asset-source abstraction with a separate design discussion. Do not port the
fork's wide permission declaration as a default.

**Acceptance:** local and cloud provider behavior is explicit; cancellation does
not publish a partial config; source files are not modified; no source is
silently substituted; tests cover path/provider failures; app does not require
unneeded all-files permission.

**Risk / complexity:** low-medium for import UX; high for true direct SAF reads.

### 4. Engine-consistent mod profile selection

Iron Harbor's JNI path asks Rust to resolve a selected mod and validate the
current selection. Upstream already has `util::mod_actions`, the in-game Mods
shell, and resource discovery. A contribution should improve the engine owner
once and expose the result, rather than have Kotlin reconstruct `[Files]`,
`[Archives]`, or INI precedence. Keep vanilla defaults unchanged, extension
switches data-driven, and active-match resource changes deferred to a safe
boundary.

**Acceptance:** resolver output is consumed by both selection display and game
load; tests cover a manifest override, a partial mod fallback and vanilla data;
no Android hardcoded profile/faction/filename table; mod caches remain isolated.

**Risk / complexity:** high, gameplay/resource loading. Needs relevant engine
tests and real mod data/device run before claiming compatibility.

### 5. Mod map catalog and import convenience

Upstream map discovery already exists; Iron Harbor supplies a launcher catalog
for nested maps, imports maps into user data, and separates mod map display.
First determine whether a specific upstream map ownership/selection gap exists.
Propose a small shared catalog/index only if engine map discovery and the shell
can consume it; do not make a launcher-only duplicate of map selection logic.

**Acceptance:** map list follows engine-supported formats, handles nested paths
and duplicates deterministically, and honors selected mod/map catalog rules.
Tests exercise discovery and import behavior. Device testing covers large trees
and read permission loss.

**Risk / complexity:** medium; format and precedence semantics may be mod data
dependent.

### 6. Diagnostics and startup failure reporting

Iron Harbor has startup crash capture, bounded log archives, preflight timings
and a sharing flow. Upstream already has Rust logging/panic hooks and SAF import
errors. A neutral contribution should be a bounded, reproducible diagnostic
bundle helper with data minimization, not an Iron Harbor-specific Downloads
filename or branding.

**Acceptance:** archives contain only known engine/crash/summary/logcat inputs,
exclude archives and partial files, enforce byte limits, keep recent fatal/ANR
context, and can be shared through Android's content URI mechanism. Tests cover
repeat exports and old ZIPs. Device check the sharesheet and app-private log
readability. Never include game assets/config secrets by default.

**Risk / complexity:** medium, privacy and lifecycle.

### 7. Camera, immersive mode and display insets

Upstream already has Android display inset plumbing and camera settings. Offer
only the smallest missing presentation preference if maintainers agree. Keep
zoom bounds in the engine's existing configuration owner and apply only through
safe event-loop messages. Do not duplicate world/screen coordinate conversion
between Kotlin and Rust. Do not force immersive mode on the launcher or SAF UI.

**Risk / complexity:** medium. Requires Android 13–16, gesture and three-button
navigation, cutout and orientation testing.

## Stop conditions

- If an upstream commit already owns the same behavior, do not submit duplicate
  functionality; use the existing API or report a concrete gap.
- If correctness depends on a device-only behavior, keep the candidate in
  “requires device testing” rather than representing it as ready.
- If the change touches simulation state, deterministic order, multiplayer
  messages, or vanilla behavior, narrow scope to the owning engine chain and
  follow upstream's native-evidence/testing contract.
- If source ownership or redistribution rights for an asset are unclear, omit
  it from the contribution.

## Source anchors

- Iron Harbor implementation: [B60 Android source](https://github.com/shietgrupe-cpu13/IronHarborMobile/tree/1293fb441727e2522f6a284d88faf4f108a1ee3e/android)
- Upstream IME/GameActivity change: [#4350 commit](https://github.com/YuriPlanet/vera20k/commit/21fa6d2f305c79fa210992885ea0845fd2c58ba7)
- Upstream in-game Mods actions: [#5220 commit](https://github.com/YuriPlanet/vera20k/commit/84ff1d6c146fb602585131b7b49d44c81b2a7cb1)
- Upstream Android workflow proposal: [PR #5256](https://github.com/YuriPlanet/vera20k/pull/5256)
