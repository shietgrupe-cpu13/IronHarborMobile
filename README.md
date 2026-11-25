# Iron Harbor Mobile

**Unofficial Android adaptation of [VERA20K](https://github.com/YuriPlanet/vera20k).**

Iron Harbor Mobile is an independently maintained, modified Android fork by Gamerbry. It is not an official VERA20K build and is not affiliated with, endorsed by, or officially supported by the upstream development team. Full credit remains with the original authors and contributors for their work.

## Project scope

This fork adapts the Rust engine for Android devices, including mobile input, Android integration and device-specific rendering and performance work. The upstream project remains the authoritative source for its official releases and support.

## Downloads and source

See this repository's [releases](https://github.com/shietgrupe-cpu13/vera20kMobile/releases) for any published builds. Players must supply their own lawfully obtained Red Alert 2: Yuri's Revenge game files.

The project's source is available in this repository under GPLv3 and the applicable additional terms. APK distributions must identify the exact corresponding source revision and provide access to the complete corresponding source, including applicable build scripts and configuration, under GPLv3 section 6. This policy applies to test builds distributed outside GitHub as well. A link to the moving main branch alone does not establish that a particular APK matches its source.

## Development and support

Android-specific source and integration files are in [android/](android/). The Rust engine is in [src/](src/). Report issues specific to this fork through this repository's [issue tracker](https://github.com/shietgrupe-cpu13/vera20kMobile/issues). For official upstream documentation and releases, visit [YuriPlanet/vera20k](https://github.com/YuriPlanet/vera20k).

## Upstream support

The original project accepts voluntary contributions through its [official repository](https://github.com/YuriPlanet/vera20k). Its contribution ledger is maintained [here](https://github.com/YuriPlanet/tibtre-ledger). This fork is independent and does not represent upstream fundraising or support.

## Credits and legal

Thanks to OpenRA, XCC Mixer, the ModEnc wiki, Project Perfect Mod, EA's GPL source release of
Command & Conquer and Red Alert, World-Altering Editor, Final Alert, YRpp, Ares, Phobos and
many others.

Copyright © 2026 Halvor Nikolai Rusten and VERA20K contributors.
Licensed under the [GPLv3](LICENSE-GPL) with [additional terms](LEGAL.md#additional-terms).
Red Alert 2 and Yuri’s Revenge retail game files are not licensed under GPLv3 and must be supplied separately by players from their own lawful copies. Command & Conquer and Red Alert are trademarks of Electronic Arts Inc. Neither this fork nor upstream is affiliated with or endorsed by Electronic Arts.

This product includes software developed by the [Ares Contributors](LICENSES/Ares.txt).
This product includes software developed by the [Phobos Developers](docs/phobos.md#original-project-and-authors).

**Read the [legal notice](LEGAL.md) before redistributing this fork or upstream code.**


## Documentation

- [WebAssembly preview](docs/wasm.md): an opt-in single-player build that runs in a browser.
- [Modding guide](docs/modding.md): what mods can and can't do, and how VERA20K differs from the original game.
- [INI profiles](docs/ini-roots.md): choosing rules, art and AI files.
- [Ares](docs/ares.md), [Phobos](docs/phobos.md), [NPatch](docs/npatch.md) and [VeraX](docs/verax.md): extension support and limitations.
- [Engine overview](docs/index.md): code layout and how the engine fits together.
- [Developer tools](tools/README.md): builds, tests and asset inspection.
- [Map testing](tools/map_observation.md): scripted scenarios, screenshots and match observations.
- [Native comparisons](tools/native_oracle.md): comparing behavior with the original game.
- [Ghidra workflow](docs/ghidra-workflow.md): inspecting the original engine.
