# Android Mental Omega compatibility notes

This is a selective integration guide for the Android client, not a claim of complete Mental Omega support. The comparison source is [928274/vera20k-MO's adaptation notes](https://github.com/928274/vera20k-MO/blob/mo/docs/MO-ADAPTATION.md). That fork documents MO-specific root/archive names and gameplay/rendering adaptations; it also warns that some earlier adaptations are now handled by common upstream runtime code and must not be reintroduced as duplicate build features.

## What the launcher and engine do

1. The launcher selects a mod directory and asks the native engine to validate the selected INI profile against the files visible in the chosen game and mod directories. It persists the config only after validation. Selecting a mod without pinning a profile probes MO roots first and YR second; vanilla probes YR first. An explicit `mo` profile is needed to choose MO's renamed startup archive family; `auto` root detection occurs after archive mounting and therefore cannot choose that archive family by itself.
2. The `mo` profile loads `rulesmo.ini`, `artmo.ini`, and `aimo.ini` (including loose files and the active MO archive family). The startup archive family uses `expandmo99.mix` down through `expandmo00.mix`, `mapsmo##.mix`, `multimo.mix`, `thememo.mix`, and `movmo##.mix`. Shared YR master/cache/language/audio/conquer/cameo archives retain their normal names. Profile and archive rules are documented in [INI roots](ini-roots.md).
3. The skirmish country/faction list is built from the active Rules country registry. A country is offered when multiplayer is enabled and it has a nonempty `UIName`; Ares `ListIndex` and registry order determine ordering. The name resolver can fall back from the `UIName` string-table key to the country's plain `Name`, but a country without `UIName` is excluded before display. This path is not a hard-coded vanilla faction list.
4. The loading screen reads the selected country's `File.LoadScreen`, `File.LoadScreenPAL`, and `LoadScreenText.*` values from the active Rules data. Those filenames are resolved by the active engine asset provider. Existing country loading coverage includes parent inheritance and custom SHP/PAL assets, but no actual MO installation was available for this validation.

Android builds must enable the `phobos` Cargo feature for Phobos-backed mods such as MO. That compiles the engine's implemented Phobos behaviors; it does not bundle or load `Phobos.dll`, the MO executable, or any game assets. Ares behavior is selected at runtime from the loaded game Rules, not by a separate `ares` Cargo feature.

On Android, the launcher can keep one user-selected `ares.mix` at
`Documents/RA2IRONHARBOR/AresSupport/ares.mix`. It mirrors that archive to the
app-private engine directory only when synchronizing a selected mod. Auto mounts
the shared archive for that mod when present, while Off disables Ares; a mod's own
`ares.mix` wins. With vanilla selected, the support directory is not synchronized
or mounted. The archive version must match the mod's Ares requirements; the launcher
does not claim that one Ares build is compatible with every mod. Changes apply on
the next game launch, before the engine reads its archive and INI sources.

## MO-fork adaptations already present in this checkout

- MO root names and archive family, plus launcher validation and automatic profile choice for a selected mod.
- MO 13-color skirmish ordering and Ares octagonal sight above the stock radius, bounded by the engine configuration.
- Failed music playback removes only that track from automatic repeat selection.
- Ares `Weapon.Anim` / `Warhead.AnimList` Find-or-Allocate handling and Phobos `SW.ShowCameo=no` filtering for the right build sidebar.
- Common-engine Ares behaviors that the MO fork documents as upstream replacements (such as `Convert.Deploy`, `NoAmmoWeapon`, `InitialPayload`, cloning facilities, and Psychic Reveal) should stay in their shared runtime owners rather than be copied from the MO branch.

The existing b9 and b10 Android adaptation reports record the focused regressions and ARM64 builds for these items: [b9 report](android-mo-adaptation-b9.md) and `out/logs/android-mo-adaptation-b10-report.md`.

## Device evidence and remaining gaps

A follow-up B27 run on 2026-11-19 used the user's Mental Omega 3.3.0 installation on the Android 16 Xiaomi (Mali-G720 MC7). The engine logged the selected `mo` profile, registered `expandmo99/97/96/95.mix` and the `mapsmo03`/`multimo`/`movmo03` family, loaded `rulesmo.ini`, `artmo.ini`, `aimo.ini`, and `thememo.ini` from `expandmo99.mix`, and transitioned a skirmish into `InGame`. Its Rules registry was much larger than vanilla YR (151 infantry, 364 vehicles, 51 aircraft, 1,157 buildings, 943 weapons, and 135 superweapon types). This confirms the actual MO rules profile and core archive path are active; it does not certify every menu row or in-game type.

That same match log records concrete incomplete areas:

- `MPModesMD.ini` names `MPBattleMO.ini`, `MPFreeForAllMO.ini`, `MPCoopMO.ini`, and `MPUnholyMO.ini`, but the asset manager could not find those files. It used native mode-constructor defaults and then an empty mode-rules layer. This can affect mode-specific lobby/rules behavior and is a likely source of discrepancies; it is not safe to silently substitute a different mode file.
- `thememo.ini` was read, but the captured archive registrations did not include `thememo.mix`; two listed music tracks (`nothing` and `solara`) could not be resolved. This is a content/lookup issue to trace against the installed MO files.
- Nine named authoritative effect assets were missing at match load and would use fallbacks. The terrain build logged 528 missing TMP draw-dimension references; the overlay atlas had 148/1,602 misses and smudges 22/46 misses. The AI diagnostic also found trigger `FOETR176LB-G` referencing absent team type `FOETT176LB-G`.
- The log shows MO's dynamic Rules registries loaded, but does not enumerate the country selector's displayed labels, prove every faction is selectable, or verify each country-specific loading screen visually. The current CSF log reports `ra2md.csf` with no supplemental tables, so custom faction label resolution needs a direct check against the MO country `UIName` keys.

The full captured log is `out/logs/b27-current-MO-ra2.log`. Next checks should compare the MO `MPModesMD.ini` rows and referenced mode files, resolve the absent asset names against the active MO archives, and verify country `UIName` keys against the loaded CSF before changing code. Gameplay paths depending on MO's executable or DLL extensions remain unsupported unless implemented by the engine; the Android build does not load those Windows binaries.

When a roster, label, or loading screen fails, collect the engine's full startup log and the exact mod folder/profile selected. Diagnose the active INI sources and asset winners first; do not add a guessed fallback or substitute vanilla data, since that can hide a broken MO install and contaminate mod behavior.
